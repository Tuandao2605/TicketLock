package com.ticketlock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lab/session")
public class SessionLabController {

    // Chỉ dùng GET thay đổi bộ đếm trong bài thực hành session này.
    @GetMapping
    public SessionStatus visit(HttpServletRequest request) {
        HttpSession session = request.getSession();
        Integer visitCount = (Integer) session.getAttribute("visitCount");
        int nextCount = visitCount == null ? 1 : visitCount + 1;
        session.setAttribute("visitCount", nextCount);
        return new SessionStatus(true, nextCount);
    }

    @GetMapping("/status")
    public SessionStatus status(HttpServletRequest request) {
        // false: chỉ tìm session hiện có, không tạo session mới.
        HttpSession session = request.getSession(false);
        if (session == null) {
            return new SessionStatus(false, 0);
        }
        Integer visitCount = (Integer) session.getAttribute("visitCount");
        return new SessionStatus(true, visitCount == null ? 0 : visitCount);
    }

    public record SessionStatus(boolean hasSession, int visitCount) {
    }
}
