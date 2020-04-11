package com.corgi.common.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.common.constant.Constants;
import com.corgi.entity.JwtUser;
import com.corgi.exception.PermissionException;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;

public class JWTUtils {
    public static long expireTime = 30 * 60 * 1000;
    private static long maxAge = 2 * 60 * 60 * 1000;
    private static final String SECRET = "Corgi-5DS4kfiL";
    public static final String JWT_USER = "jwtUser";
    public static final String JWT_HEADER = "jwt";
    public static final String ADMIN_ID = "-1";

    public static String createJWT(String userId, String version) {
        return createJWT(userId, version, maxAge);
    }

    public static String createJWT(String userId, String version, long newMaxAge) {
        long now = System.currentTimeMillis();
        try {
            final Algorithm signer = Algorithm.HMAC256(SECRET);
            HashMap header = new HashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            String token = JWT.create()
                    .withHeader(header)
                    .withClaim(JwtUser.USER_ID, userId)
                    .withClaim(JwtUser.VERSION, version)
                    .withIssuedAt(new Date())
                    .withExpiresAt(new Date(now + newMaxAge))
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
    public static DecodedJWT verifyToken(String token) throws UnsupportedEncodingException {
        Algorithm algorithm = Algorithm.HMAC256(SECRET);
        JWTVerifier verifier = JWT.require(algorithm).build();
        DecodedJWT jwt = verifier.verify(token);
        return jwt;
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
