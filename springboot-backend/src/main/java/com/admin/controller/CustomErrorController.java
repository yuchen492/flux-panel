package com.admin.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public void handleError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        
        response.setContentType("text/html; charset=UTF-8");
        
        if (status != null) {
            int statusCode = Integer.valueOf(status.toString());
            
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                String html = generateErrorHtml(
                    "404",
                    "你推开了后端的大门，却发现里面只有寂寞。",
                    request.getRequestURI()
                );
                response.getWriter().write(html);
            }
        } 
    }
    
    private String generateErrorHtml(String statusCode, String title, String path) {
        return "<!DOCTYPE html>" +
                "<html lang='zh-CN'>" +
                "<head>" +
                "    <meta charset='UTF-8'>" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "    <title>错误 " + statusCode + "</title>" +
                "    <style>" +
                "        body {" +
                "            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif;" +
                "            margin: 0;" +
                "            padding: 0;" +
                "            min-height: 100vh;" +
                "            display: flex;" +
                "            flex-direction: column;" +
                "            justify-content: center;" +
                "            align-items: center;" +
                "            text-align: center;" +
                "        }" +
                "        .error-code {" +
                "            font-size: 6rem;" +
                "            color: #333;" +
                "            font-weight: 300;" +
                "            margin: 0;" +
                "        }" +
                "        .error-title {" +
                "            font-size: 1.2rem;" +
                "            color: #666;" +
                "            margin: 20px 0 0 0;" +
                "            font-weight: normal;" +
                "        }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class='error-code'>" + statusCode + "</div>" +
                "    <div class='error-title'>" + title + "</div>" +
                "</body>" +
                "</html>";
    }
} 