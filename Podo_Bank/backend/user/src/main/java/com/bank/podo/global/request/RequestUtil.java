package com.bank.podo.global.request;

import com.bank.podo.domain.user.dto.CheckSuccessCodeDTO;
import com.bank.podo.domain.user.dto.ResetPasswordDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequestUtil {

    @Value("${http.request.account.url}")
    private String accountUrl;

    @Value("${http.request.auth.url}")
    private String authUrl;

    private final RequestApi requestApi;

    public boolean checkAccountBalanceZero(Long userId) {
        String url = accountUrl + "/api/v1/account/" + userId;

        return requestApi.apiGet(url);
    }

    public boolean removeRefreshToken(String email) {
        String url = authUrl + "/api/v1/auth/logout";

        return requestApi.apiPost(url, email);
    }

    public boolean checkVerificationSuccess(CheckSuccessCodeDTO checkSuccessCodeDTO) {
        String url = authUrl + "/api/v1/auth/checkSuccessCode";

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(checkSuccessCodeDTO);
            return requestApi.apiPost(url, json);
        } catch (Exception e) {
            return false;
        }

    }
}
