//controller
package com.example.controllers;

import com.example.utils.HttpUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;  // or:  jakarta.servlet.http.HttpServletRequest for >6.1

@Controller
public class ClientIPAddressController {

    @RequestMapping(
        method = RequestMethod.GET,
        value = "/client-ip-address",
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    @ResponseBody
    public String getClientIPAddress(HttpServletRequest request) {
        String ip = HttpUtils.getRequestIP(request);
        return "Client IP Address: " + ip;
    }
}

//utils
package com.example.utils;

import javax.servlet.http.HttpServletRequest;  // or:  jakarta.servlet.http.HttpServletRequest

public final class HttpUtils {

    private static final String[] IP_HEADERS = {
        "X-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_X_FORWARDED_FOR",
        "HTTP_X_FORWARDED",
        "HTTP_X_CLUSTER_CLIENT_IP",
        "HTTP_CLIENT_IP",
        "HTTP_FORWARDED_FOR",
        "HTTP_FORWARDED",
        "HTTP_VIA",
        "REMOTE_ADDR"

        // you can add more matching headers here ...
    };

    private HttpUtils() {
        // nothing here ...
    }

    public static String getRequestIP(HttpServletRequest request) {
        for (String header: IP_HEADERS) 
            String value = request.getHeader(header);
            if (value == null || value.isEmpty()) {
                continue;
            }
            String[] parts = value.split("\\s*,\\s*");
            return parts[0];
        }
        return request.getRemoteAddr();
    }
}
