package com.gatf.selenium;

import javax.crypto.Cipher;

public class Test  {
    public static void main(String[] args) {
        try {
            //Security.setProperty("crypto.policy", "limited");
          System.out.println("Hello World!");
          int maxKeyLen = Cipher.getMaxAllowedKeyLength("AES");
          System.out.println(maxKeyLen);
          boolean unlimited =
                  Cipher.getMaxAllowedKeyLength("RC5") >= 256;
                System.out.println("Unlimited cryptography enabled: " + unlimited);
        } catch (Exception e){
          System.out.println("Sad world :(");
        }
      }
}
