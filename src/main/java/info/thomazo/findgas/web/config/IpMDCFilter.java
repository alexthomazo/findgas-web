package info.thomazo.findgas.web.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
public class IpMDCFilter implements Filter {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		try {
			MDC.put("ip", getIp(request));
			chain.doFilter(request, response);

		} finally {
			MDC.remove("ip");
		}
	}

	public String getIp(ServletRequest request) {

		if (request instanceof HttpServletRequest) {
			String forwarded = ((HttpServletRequest) request).getHeader("X-Forwarded-For");
			if (forwarded != null) {
				return forwarded;
			}
		}

		return request.getRemoteHost();
	}

	public String getIpV4(ServletRequest request) {
		String ip = getIp(request);
		if (ip.contains(".")) {
			return ip;
		} else {
			//ipv6, discard
			return null;
		}
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void destroy() {
	}
}
