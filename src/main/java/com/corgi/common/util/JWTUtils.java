package com.corgi.common.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.common.constant.Constants;
import com.corgi.exception.PermissionException;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;

public class JWTUtils {
    public static long expireTime = 30 * 60 * 1000;
    private static long maxAge = 2 * 60 * 60 * 1000;
    private static String secret = "Corgi-5DS4kfiL";

    public static String createJWT(String userId, String version) {
        long now = System.currentTimeMillis();
        try {
            final Algorithm signer = Algorithm.HMAC256(secret);
            HashMap header = new HashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            String token = JWT.create()
                    .withHeader(header)
                    .withClaim("userId", userId)
                    .withClaim("version", version)
                    .withIssuedAt(new Date())
                    .withExpiresAt(new Date(now + maxAge))
                    .sign(signer);
            return token;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 解析验证token
     *
     * @param token 加密后的token字符串
     * @return
     */
    public static DecodedJWT verifyToken(String token) throws PermissionException {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);
            return jwt;
        } catch (IllegalArgumentException e) {
            throw new PermissionException(Constants.JWT_ERROR_CODE, e.getMessage());
        } catch (JWTVerificationException e) {
            throw new PermissionException(Constants.JWT_ERROR_CODE, e.getMessage());
        } catch (UnsupportedEncodingException e) {
            throw new PermissionException(Constants.JWT_ERROR_CODE, e.getMessage());
        }
    }

    /**
     * 验证token
     *
     * @param token 加密后的token字符串
     * @return
     */
    public static DecodedJWT decodeToken(String token) {
        return JWT.decode(token);
    }

}
