package com.yongy.dotori.domain.account.service;

import com.yongy.dotori.domain.account.dto.AccountDTO;

import java.util.List;

public interface AccountService {
    public List<AccountDTO> findAllAccount();
}
