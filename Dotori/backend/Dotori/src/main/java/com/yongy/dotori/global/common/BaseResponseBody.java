package com.yongy.dotori.global.common;

import com.yongy.dotori.domain.KakaoDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseResponseBody<T>{
    private int status;
    private T data;



    public static <T> BaseResponseBody<T> of(int status, T data) {
        BaseResponseBody<T> body = new BaseResponseBody<>();
        body.setStatus(status);
        body.setData(data);
        return body;
    }
}
