package studio.forma.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded, single-instance protection. Proxy headers are deliberately not trusted. */
public class AbuseProtectionFilter extends OncePerRequestFilter {
  private static final int MAX_BODY = 16_384;
  private final Map<String, Window> windows = new HashMap<>();
  private final Clock clock;

  public AbuseProtectionFilter() {
    this(Clock.systemUTC());
  }

  AbuseProtectionFilter(Clock clock) {
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    boolean login = path.equals("/login") && request.getMethod().equals("POST");
    boolean write =
        path.startsWith("/api/")
            && (request.getMethod().equals("POST") || request.getMethod().equals("PATCH"));
    if (!login && !write) {
      chain.doFilter(request, response);
      return;
    }
    if (!allow(request.getRemoteAddr() + (login ? ":login" : ":api"), login ? 10 : 60)) {
      response.setHeader("Retry-After", "60");
      reject(response, 429, "Слишком много запросов. Подождите минуту.");
      return;
    }
    if (write) {
      byte[] bytes = request.getInputStream().readNBytes(MAX_BODY + 1);
      if (bytes.length > MAX_BODY) {
        reject(response, 413, "Размер запроса превышает 16 КБ.");
        return;
      }
      var wrapped =
          new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
              var input = new ByteArrayInputStream(bytes);
              return new ServletInputStream() {
                @Override
                public int read() {
                  return input.read();
                }

                @Override
                public boolean isFinished() {
                  return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                  return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                  throw new UnsupportedOperationException("Synchronous requests only");
                }
              };
            }

            @Override
            public BufferedReader getReader() {
              return new BufferedReader(
                  new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            }
          };
      chain.doFilter(wrapped, response);
    } else chain.doFilter(request, response);
  }

  synchronized boolean allow(String key, int limit) {
    long minute = clock.millis() / 60_000;
    if (windows.size() >= 10_000)
      windows.entrySet().removeIf(entry -> entry.getValue().minute != minute);
    Window window = windows.get(key);
    if (window == null || window.minute != minute) {
      if (window == null && windows.size() >= 10_000) return false;
      windows.put(key, new Window(minute, 1));
      return true;
    }
    if (window.count >= limit) return false;
    window.count++;
    return true;
  }

  private void reject(HttpServletResponse response, int status, String detail) throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
  }

  private static final class Window {
    final long minute;
    int count;

    Window(long minute, int count) {
      this.minute = minute;
      this.count = count;
    }
  }
}
