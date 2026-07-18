package com.prayerlink.common.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class HeaderToCookieFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            String authToken = req.getHeader("X-Auth-Token");
            String deviceId = req.getHeader("X-Device-Id");

            if (authToken != null || deviceId != null) {
                HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(req) {
                    @Override
                    public Cookie[] getCookies() {
                        List<Cookie> cookies = new ArrayList<>();
                        Cookie[] existingCookies = super.getCookies();
                        if (existingCookies != null) {
                            cookies.addAll(Arrays.asList(existingCookies));
                        }
                        
                        if (authToken != null) {
                            cookies.add(new Cookie("pl-auth-token", authToken));
                        }
                        if (deviceId != null) {
                            cookies.add(new Cookie("pl-device-id", deviceId));
                        }
                        
                        return cookies.toArray(new Cookie[0]);
                    }
                };
                chain.doFilter(wrapper, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
