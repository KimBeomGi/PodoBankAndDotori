package com.yongy.dotoripurposeservice.domain.purpose.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongy.dotoripurposeservice.domain.purpose.dto.*;
import com.yongy.dotoripurposeservice.domain.purpose.dto.communication.*;
import com.yongy.dotoripurposeservice.domain.purpose.entity.Purpose;
import com.yongy.dotoripurposeservice.domain.purpose.exception.NotActiveException;
import com.yongy.dotoripurposeservice.domain.purpose.exception.NotEqualsBalanceException;
import com.yongy.dotoripurposeservice.domain.purpose.repository.PurposeRepository;
import com.yongy.dotoripurposeservice.domain.purposeData.dto.PurposeDataDTO;
import com.yongy.dotoripurposeservice.domain.purposeData.entity.PurposeData;
import com.yongy.dotoripurposeservice.domain.purposeData.repository.PurposeDataRepository;
import com.yongy.dotoripurposeservice.domain.user.entity.User;
import com.yongy.dotoripurposeservice.global.common.CallServer;
import com.yongy.dotoripurposeservice.global.common.PodoBankInfo;
import com.yongy.dotoripurposeservice.global.redis.entity.BankAccessToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PurposeServiceImpl implements PurposeService{

    private final PurposeRepository purposeRepository;
    private final PurposeDataRepository purposeDataRepository;
    private final CallServer callServer;
    private final PodoBankInfo podoBankInfo;

    @Value("${dotori.main.url}")
    private String MAIN_SERVICE_URL;

    private static HashMap<String, Object> bodyData;

    private ResponseEntity<String> response;

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void createPurpose(PurposeDTO purposeDTO) {
        User loginUser = this.getLoginUser();
        // 새로운 목표 DB에 저장
        purposeRepository.save(Purpose.builder()
                        .userSeq(loginUser.getUserSeq()) // TODO : 통신
                        .purposeTitle(purposeDTO.getPurposeTitle())
                        .goalAmount(purposeDTO.getGoalAmount())
                        .currentBalance(BigDecimal.ZERO)
                        .startedAt(purposeDTO.getStartedAt())
                        .endAt(purposeDTO.getEndAt())
                        .terminateAt(null)
                        .finished(false)
                .build());
    }

    @Override
    public PurposeAllDTO findAllPurpose(Long userSeq) {

        List<Purpose> purposeList = purposeRepository.findAllByUserSeqAndFinishedIsFalse(userSeq);

        List<PurposeListDTO> list = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        // 전체 목표에서 title, currentBalance, goalAmount 데이터만 뽑아오기
        for(Purpose p : purposeList){

            list.add(PurposeListDTO.builder()
                            .purposeSeq(p.getPurposeSeq())
                            .title(p.getPurposeTitle())
                            .currentBalance(p.getCurrentBalance())
                            .goalAmount(p.getGoalAmount())
                            .terminatedAt(p.getTerminateAt())
                            .startedAt(p.getStartedAt())
                    .build());
            total.add(p.getCurrentBalance()); // 현재 총 저축액 구하기 위해서 각 목표의 currentBalance 합 구하기
        }

        // PurposeALlDTO 형태로 반환
        PurposeAllDTO result = PurposeAllDTO.builder()
                .currentTotalSavings(total)
                .purposeList(list)
                .build();
        return result;
    }


    @Override
    public PurposeDetailDTO findPurposeDetail(Long purposeSeq) {
        // 목표 상세 내역 조회
        Purpose purpose = purposeRepository.findByPurposeSeq(purposeSeq);
        List<PurposeData> list = purposeDataRepository.findAllByPurpose(purpose);
        List<PurposeDataDTO> purposeData = new ArrayList<>();

        for(PurposeData data : list){
            //Account account = accountRepository.findByAccountSeqAndDeleteAtIsNull(data.getAccountSeq());
            bodyData.clear();
            bodyData.put("accountSeq", data.getAccountSeq());

            response = callServer.getHttpBodyAndSend(MAIN_SERVICE_URL+"/account/communication/getTitle", HttpMethod.GET, bodyData);
            log.info("TEST - 1 : "+ response.getBody().toString());
            purposeData.add(PurposeDataDTO.builder()
                            .dataName(response.getBody().toString())
                            .dataAmount(data.getDataAmount())
                            .dataCurrentBalance(data.getDataCurrentBalance())
                            .dataCreatedAt(data.getDataCreatedAt().format(formatter))
                    .build());
        }

        PurposeDetailDTO detail = PurposeDetailDTO.builder()
                .purposeTitle(purpose.getPurposeTitle())
                .currentBalance(purpose.getCurrentBalance())
                .goalAmount(purpose.getGoalAmount())
                .startedAt(purpose.getStartedAt().toString())
                .endAt(purpose.getEndAt().toString())
                .purposeDataList(purposeData)
                .build();

        return detail;
    }


    @Override
    public void terminatePurpose(Long purposeSeq) {
        // 목표 중단
        Purpose purpose = purposeRepository.findByPurposeSeq(purposeSeq);

        purpose.update(Purpose.builder()
                .endAt(LocalDate.now())
                .terminateAt(LocalDateTime.now())
                .build());
    }

    @Override
    public PurposeSummaryDTO summarizePurpose(Long purposeSeq){
        Purpose purpose = purposeRepository.findByPurposeSeq(purposeSeq);
        BigDecimal goal = purpose.getGoalAmount();
        BigDecimal current = purpose.getCurrentBalance();
        BigDecimal percentage = BigDecimal.ZERO;

        // TODO 현재 진행된 퍼센트(or 남은 퍼센트) 정해야 됨
        if(goal != BigDecimal.ZERO || current != BigDecimal.ZERO){
            percentage = current.divide(goal, BigDecimal.ROUND_HALF_UP);
            percentage = percentage.multiply(new BigDecimal("100"));
        }

        PurposeSummaryDTO summary = PurposeSummaryDTO.builder()
                .purposeTitle(purpose.getPurposeTitle())
                .goalAmount(purpose.getGoalAmount())
                .currentBalance(purpose.getCurrentBalance())
                .restAmount(purpose.getGoalAmount().subtract(purpose.getCurrentBalance()))
                .percentage(percentage)
                .build();

        return summary;
    }

    @Override
    public void saving(SavingDataDTO savingDataDTO) {

        List<PurposeData> purposeDataList = new ArrayList<>();
        BigDecimal totalSaving = new BigDecimal(BigInteger.ZERO);

        // 1. 각 목표에 따라 저축 금액 update 하기
        for (PurposeSavingDTO data : savingDataDTO.getSavingDTO().getPurposeSavingList()) {
            Purpose purpose = purposeRepository.findByPurposeSeq(data.getPurposeSeq());

            // 현재 금액에 저축 금액 더하기
            purpose.addCurrentBalance(data.getSavingAmount());
            purposeRepository.save(purpose);
            log.info(purpose.getCurrentBalance()+"");

            // 1-2. purpose_data 저장하기
            // 각 purpose에 연결된 purpose_data에 저장
            purposeDataList.add(PurposeData.builder()
                    .accountSeq(savingDataDTO.getAccountSeq())
                    .dataAmount(data.getSavingAmount())
                    .purpose(purpose)
                    .dataCurrentBalance(purpose.getCurrentBalance())
                    .dataCreatedAt(LocalDateTime.now())
                    .build());

            totalSaving = totalSaving.add(data.getSavingAmount());
        }

        if(!totalSaving.equals(savingDataDTO.getSavingDTO().getTotalSaving())){
            throw new IllegalArgumentException("총 저축 금액이 일치하지 않습니다.");
        }

        purposeDataRepository.saveAll(purposeDataList);
    }

    @Override
    public void purposeFinised(PurposeFinisedDTO purposeFinisedDTO) throws JsonProcessingException, ParseException {
        Purpose purpose = purposeRepository.findByPurposeSeq(purposeFinisedDTO.getPuposeSeq());

        if(purpose.getTerminateAt() == null){
            throw new NotActiveException("종료된 목표가 아닙니다.");
        }

        if(!purpose.getCurrentBalance().equals(purposeFinisedDTO.getPurposeSavings())){
            throw new NotEqualsBalanceException("저축 금액이 일치하지 않습니다.");
        }

        // TODO : 금액만큼 은행에 송금 요청
        // TODO : 1. 은행 정보 가져오기
        MultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
        parameters.add("bankSeq", purposeFinisedDTO.getBankSeq());
        ResponseEntity<String> bankResponse = callServer.getHttpWithParamsAndSend(MAIN_SERVICE_URL+"/api/v1/bank/communication/bankInfo", parameters);

        String responseCode = bankResponse.getStatusCode().toString().split(" ")[0];
        String responseContent = bankResponse.getBody();

        if(responseCode.equals("200")){
            ObjectMapper objectMapper = new ObjectMapper();
            BankDTO bankInfo = objectMapper.readValue(responseContent,BankDTO.class);

            // TODO : 2. 은행 정보와 계좌정보 바탕으로 account 정보 가져오기
            HashMap<String, Object> body = new HashMap<>();
            body.put("accountName", purposeFinisedDTO.getAccountNumber());
            ResponseEntity<String> accountResponse = callServer.getHttpBodyAndSend(MAIN_SERVICE_URL+"/api/v1/account/communication/account", HttpMethod.GET, body);

            responseCode = accountResponse.getStatusCode().toString().split(" ")[0];
            responseContent = accountResponse.getBody();

            AccountFintechCodeDTO accountFintechCodeDTO = objectMapper.readValue(responseContent, AccountFintechCodeDTO.class);

            // TODO : 3. 은행에 송금 요청하기
            this.callBankAPI(bankInfo, accountFintechCodeDTO, purposeFinisedDTO);
        }
    }

    public void callBankAPI(BankDTO bankInfo, AccountFintechCodeDTO accountFintechCodeDTO, PurposeFinisedDTO purposeFinisedDTO) throws ParseException {
        String bankAccessToken = podoBankInfo.getConnectionToken(bankInfo); // 은행 accessToken 가져오기
        String accessToken = null;
        Purpose purpose = purposeRepository.findByPurposeSeq(purposeFinisedDTO.getPuposeSeq());

        if(bankAccessToken != null){ // accessToken이 없으면
            accessToken = bankAccessToken;
        }

        if(bankAccessToken == null){
            accessToken = podoBankInfo.getConnectionToken(bankInfo);
        }
        // NOTE : plan에 연결된 계좌에서 총 금액을 도토리 계좌로 입금 요청하기
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Content-Type", "application/json;charset=utf-8");
        httpHeaders.add("Authorization","Bearer " + accessToken);

        Map<String, String> bodyData = new HashMap<>();
        bodyData.put("serviceCode", bankInfo.getServiceCode());
        bodyData.put("fintechCode", accountFintechCodeDTO.getFintechCode());
        bodyData.put("amount", String.valueOf(purposeFinisedDTO.getPurposeSavings()));
        bodyData.put("content", "도토리 "+purpose.getPurposeTitle());

        HttpEntity<Map<String, String>> httpEntity = new HttpEntity<>(bodyData, httpHeaders);

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(
                bankInfo.getBankUrl()+"/api/v1/fintech/deposit",
                HttpMethod.POST,
                httpEntity,
                String.class
        );

        String responseCode = response.getStatusCode().toString().split(" ")[0];
        if(!responseCode.equals("200")){
            throw new IllegalArgumentException("송금에 실패했습니다.");
        }
    }


    public User getLoginUser(){
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
