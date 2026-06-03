package com.zubairmuwwakil.marketdata.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {

    @GetMapping("/")
    public String home() {
        // Serve the static frontend
        return "forward:/index.html";
    }
}
