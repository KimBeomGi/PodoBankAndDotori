package com.bank.podo.domain.account.service;

import com.bank.podo.domain.account.dto.*;
import com.bank.podo.domain.account.entity.Account;
import com.bank.podo.domain.account.entity.TransactionHistory;
import com.bank.podo.domain.account.enums.TransactionType;
import com.bank.podo.domain.account.exception.AccountNotFoundException;
import com.bank.podo.domain.account.exception.InsufficientBalanceException;
import com.bank.podo.domain.account.exception.PasswordRetryCountExceededException;
import com.bank.podo.domain.account.repository.AccountRepository;
import com.bank.podo.domain.account.repository.TransactionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAdminService {

    private final AccountService accountService;

    private final AccountRepository accountRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;

    @Transactional(noRollbackFor = PasswordRetryCountExceededException.class)
    public void transfer(AdminTransferDTO transferDTO) {
        Account senderAccount = accountRepository.findByAccountNumberAndDeletedFalse(transferDTO.getSenderAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다."));
        Account receiverAccount = accountRepository.findByAccountNumberAndDeletedFalse(transferDTO.getReceiverAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다."));

        BigDecimal transferAmount = transferDTO.getAmount();

        accountService.withdraw(senderAccount, transferAmount, transferDTO.getSenderContent(), null);
        accountService.deposit(receiverAccount, transferAmount, transferDTO.getReceiverContent(), null);

        logTransfer(senderAccount, receiverAccount, transferAmount);
    }

    public AdminBalanceDTO balance(AdminBalanceDTO balanceDTO) {
        Account account = accountRepository.findByAccountNumberAndDeletedFalse(balanceDTO.getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다."));

        balanceDTO.setBalance(account.getBalance());

        logBalance(account);

        return balanceDTO;
    }

    public List<TransactionHistoryDTO> history(AdminHistoryDTO historyDTO) {
        Account account = accountRepository.findByAccountNumberAndDeletedFalse(historyDTO.getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다."));

        List<TransactionHistory> transactionHistoryList =
                transactionHistoryRepository.findAllByAccountAndCreatedAtGreaterThanEqual(account, historyDTO.getStartAt());

        logHistory(account);

        return toTransactionHistoryDTOList(transactionHistoryList);
    }

    private List<TransactionHistoryDTO> toTransactionHistoryDTOList(List<TransactionHistory> transactionHistoryList) {
        return transactionHistoryList.stream()
                .map(this::toTransactionHistoryDTO)
                .collect(Collectors.toList());
    }

    private TransactionHistoryDTO toTransactionHistoryDTO(TransactionHistory transactionHistory) {
        TransactionHistoryDTO.TransactionHistoryDTOBuilder builder = TransactionHistoryDTO.builder()
                .transactionType(transactionHistory.getTransactionType())
                .transactionAt(transactionHistory.getCreatedAt())
                .amount(transactionHistory.getAmount())
                .balanceAfter(transactionHistory.getBalanceAfter())
                .businessCode(transactionHistory.getBusinessCode())
                .content(transactionHistory.getContent());

        if (transactionHistory.getCounterAccount() != null) {
            builder.counterAccountName(transactionHistory.getCounterAccount().getUser().getName());
        }

        return builder.build();
    }

    private void logTransfer(Account senderAccount, Account receiverAccount, BigDecimal transferAmount) {
        log.info("=====" + "\t"
                + "송금(Admin)" + "\t"
                + "송금 계좌 번호: " + senderAccount.getAccountNumber() + "\t"
                + "수신 계좌 번호: " + receiverAccount.getAccountNumber() + "\t"
                + "송금액: " + transferAmount + "\t"
                + "=====");
    }

    private void logBalance(Account account) {
        log.info("=====" + "\t"
                + "잔액 조회(Admin)" + "\t"
                + "계좌 번호: " + account.getAccountNumber() + "\t"
                + "=====");
    }

    private void logHistory(Account account) {
        log.info("===== " + "\t"
                + "거래 내역 조회(Admin)" + "\t"
                + "계좌 번호: " + account.getAccountNumber() + "\t"
                + " =====");
    }
}