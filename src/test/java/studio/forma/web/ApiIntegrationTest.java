package studio.forma.web;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import studio.forma.catalog.ServiceCode;
import studio.forma.inquiry.InquiryRepository;
import studio.forma.inquiry.InquiryService;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired InquiryRepository repository;
  @Autowired InquiryService inquiryService;

  @BeforeEach
  void clean() {
    repository.deleteAll();
  }

  Map<String, Object> request() {
    return new LinkedHashMap<>(
        Map.of(
            "requestId",
            UUID.randomUUID().toString(),
            "name",
            "Анна",
            "email",
            "anna@example.com",
            "service",
            "INTERIOR",
            "area",
            80,
            "message",
            "Светлый интерьер",
            "consent",
            true));
  }

  String create(Map<String, Object> request) throws Exception {
    return mvc.perform(
            post("/api/inquiries")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("estimateMin").value(320000))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void servesLandingAndCatalogWithSecurityHeaders() throws Exception {
    mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    mvc.perform(get("/index.html"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("FORMA")))
        .andExpect(header().exists("Content-Security-Policy"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    mvc.perform(get("/api/services"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
    mvc.perform(get("/api/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("token").isString());
  }

  @Test
  void persistsAndDeduplicatesIdenticalRetry() throws Exception {
    var payload = request();
    String first = create(payload);
    String second = create(payload);
    assertThat(mapper.readTree(first).get("id")).isEqualTo(mapper.readTree(second).get("id"));
    assertThat(repository.count()).isEqualTo(1);
    payload.put("area", 90);
    mvc.perform(
            post("/api/inquiries")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectsInvalidInputWithoutSaving() throws Exception {
    var payload = request();
    payload.put("email", "invalid");
    payload.put("area", 0);
    payload.put("consent", false);
    mvc.perform(
            post("/api/inquiries")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("errors.email").exists())
        .andExpect(jsonPath("errors.area").exists())
        .andExpect(jsonPath("errors.consent").exists());
    assertThat(repository.count()).isZero();
  }

  @Test
  void rejectsWhitespaceNameAndOversizedMessage() throws Exception {
    var payload = request();
    payload.put("name", "  ");
    payload.put("message", "x".repeat(2001));
    mvc.perform(
            post("/api/inquiries")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsUnknownFieldsAndMalformedJson() throws Exception {
    var payload = request();
    payload.put("estimateMin", 1);
    mvc.perform(
            post("/api/inquiries")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/estimates")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void enforcesCsrfAndPrivateAccess() throws Exception {
    mvc.perform(
            post("/api/estimates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"service\":\"INTERIOR\",\"area\":80}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/inquiries")).andExpect(status().isUnauthorized());
    mvc.perform(get("/studio.html")).andExpect(status().is3xxRedirection());
    mvc.perform(get("/api/admin/inquiries").with(user("visitor").roles("VISITOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  void estimatesValidatesBoundsAndEnum() throws Exception {
    mvc.perform(
            post("/api/estimates")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"service\":\"ARCHITECTURE\",\"area\":100}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("min").value(250000));
    mvc.perform(
            post("/api/estimates")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"service\":\"OTHER\",\"area\":80}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void protectsWorkflowAndOptimisticVersion() throws Exception {
    var receipt = mapper.readTree(create(request()));
    String id = receipt.get("id").asText();
    String path = "/api/admin/inquiries/" + id + "/status";
    mvc.perform(
            patch(path)
                .with(user("studio").roles("STUDIO"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CONTACTED\",\"version\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(
            patch(path)
                .with(user("studio").roles("STUDIO"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REVIEWING\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("version").value(1));
    mvc.perform(
            patch(path)
                .with(user("studio").roles("STUDIO"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CLOSED\",\"version\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/admin/inquiries?status=REVIEWING").with(user("studio").roles("STUDIO")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalElements").value(1))
        .andExpect(jsonPath("items[0].email").value("anna@example.com"));
    mvc.perform(get("/api/admin/inquiries?status=NEW").with(user("studio").roles("STUDIO")))
        .andExpect(jsonPath("totalElements").value(0));
  }

  @Test
  void missingInquiryAndInvalidPageAreHandled() throws Exception {
    mvc.perform(
            patch("/api/admin/inquiries/" + UUID.randomUUID() + "/status")
                .with(user("studio").roles("STUDIO"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REVIEWING\",\"version\":0}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/admin/inquiries?page=-1").with(user("studio").roles("STUDIO")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsOversizedBody() throws Exception {
    mvc.perform(
            post("/api/inquiries")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(16385)))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void loginUsesConfiguredAccount() throws Exception {
    mvc.perform(
            post("/login")
                .with(csrf())
                .param("username", "test-studio")
                .param("password", "test-password-only-123"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/studio.html"));
    mvc.perform(
            post("/login").with(csrf()).param("username", "test-studio").param("password", "wrong"))
        .andExpect(redirectedUrl("/login.html?error"));
  }

  @Test
  void concurrentRetriesProduceExactlyOneRow() throws Exception {
    var request =
        new ApiModels.CreateInquiry(
            UUID.randomUUID(), "Анна", "anna@example.com", ServiceCode.INTERIOR, 80, "Бриф", true);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<ApiModels.Receipt> create =
          () -> {
            start.await();
            return inquiryService.create(request);
          };
      var first = executor.submit(create);
      var second = executor.submit(create);
      start.countDown();
      assertThat(first.get(10, TimeUnit.SECONDS).id())
          .isEqualTo(second.get(10, TimeUnit.SECONDS).id());
      assertThat(repository.count()).isEqualTo(1);
    }
  }

  @Test
  void paginatesAtTwelveAndRequiresExplicitVersion() throws Exception {
    for (int index = 0; index < 13; index++) {
      inquiryService.create(
          new ApiModels.CreateInquiry(
              UUID.randomUUID(), "Анна", "anna@example.com", ServiceCode.INTERIOR, 80, "", true));
    }
    mvc.perform(get("/api/admin/inquiries").with(user("studio").roles("STUDIO")))
        .andExpect(jsonPath("items.length()").value(12))
        .andExpect(jsonPath("totalPages").value(2))
        .andExpect(jsonPath("totalElements").value(13));
    mvc.perform(get("/api/admin/inquiries?page=1").with(user("studio").roles("STUDIO")))
        .andExpect(jsonPath("items.length()").value(1));
    mvc.perform(
            patch("/api/admin/inquiries/" + repository.findAll().getFirst().getId() + "/status")
                .with(user("studio").roles("STUDIO"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REVIEWING\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void preservesHttpSemantics() throws Exception {
    mvc.perform(
            post("/api/estimates").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("data"))
        .andExpect(status().isUnsupportedMediaType());
    mvc.perform(get("/api/estimates")).andExpect(status().isMethodNotAllowed());
  }
}
