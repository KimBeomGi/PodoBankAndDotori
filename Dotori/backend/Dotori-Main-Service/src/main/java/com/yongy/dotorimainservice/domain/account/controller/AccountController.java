package com.yongy.dotorimainservice.domain.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yongy.dotorimainservice.domain.account.dto.AccountDTO;
import com.yongy.dotorimainservice.domain.account.dto.communication.UserSeqDto;
import com.yongy.dotorimainservice.domain.account.service.AccountServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountServiceImpl accountService;

    @Operation(summary = "전체 계좌 조회")
    @GetMapping()
    public ResponseEntity<List<AccountDTO>> findAllAccount() throws JsonProcessingException {
        List<AccountDTO> result = accountService.findAllAccount();
        return ResponseEntity.ok(result);
    }

    // ------- 통신 --------
    // NOTE : 사용자의 계좌를 삭제한다.
    @PostMapping("/communication/delete/all")
    public ResponseEntity<String> deleteUserAccount(@RequestBody UserSeqDto userSeqDto){
        log.info("--come--");
        accountService.removeUserAccounts(userSeqDto.getUserSeq());
        log.info("--test--");
        return ResponseEntity.ok().build();
    }

    // NOTE :

}
