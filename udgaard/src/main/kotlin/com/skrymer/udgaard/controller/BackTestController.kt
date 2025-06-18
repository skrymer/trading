package com.skrymer.udgaard.controller

import com.skrymer.udgaard.service.StockService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController("/udgaard")
class BackTestController(val stockService: StockService) {

//    @GetMapping("/report")
}