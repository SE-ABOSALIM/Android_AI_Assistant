package com.example.anroidaiassistant;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // BURAYI KENDI PC IP ADRESINE GORE DEGISTIR
    // sondaki / olacak
    private static final String BASE_URL = "http://10.245.3.102:8000/";

    private static Retrofit retrofit;

    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}