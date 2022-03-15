package com.corgi.websocket;

import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.common.constant.Constants;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.util.JWTUtils;
import com.corgi.entity.CorgiUserVipDetail;
import com.corgi.entity.JwtUser;
import com.corgi.service.CorgiUtilService;
import com.corgi.service.MQService;
import com.corgi.user.api.CorgiFeedService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.CorgiFeed;
import com.corgi.user.entity.UserLogin;
import com.corgi.user.entity.UserPosition;
import jdk.nashorn.internal.ir.annotations.Reference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

@Slf4j
@ServerEndpoint("/corgiSocket")
@Component
public class CorgiSocket {
    @Reference
    private CorgiFeedService corgiFeedService;
    @Reference
    private CorgiUserService corgiUserService;

    @Autowired
    private CorgiUtilService corgiUtilService;
    @Autowired
    private MQService mqService;

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) {
        session.setMaxIdleTimeout(600000);
        log.info("有新连接加入：{}，当前在线人数为：{}", session.getId());
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session) {
        log.info("有一连接关闭：{}，当前在线人数为：{}");
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            log.info("websocket message:{} ", message);
            JSONObject messageObj = JSONObject.parseObject(message);
            String jwt = messageObj.getString("jwt");
            String type = messageObj.getString("type");
            JwtUser user;
            switch (type) {
                case "list":
                    user = this.verifyToken(jwt, session);
                    if (user == null) {
                        return;
                    }
                    String[] activityIds = messageObj.getString("activityIds").split(",");
                    for (int i = 0; i < activityIds.length; i++) {
                        if (corgiUtilService.lock("view_" + activityIds[i])) {
                            try {
                                CorgiFeed feed = new CorgiFeed();
                                feed.setUserId(user.getUserId());
                                feed.setFeed(activityIds[i]);
                                corgiFeedService.viewFeed(feed);
                            } finally {
                                corgiUtilService.unlock("view_" + activityIds[i]);
                            }
                        }
                    }
                    break;
                case "detail":
                    user = this.verifyToken(jwt, session);
                    if (user == null) {
                        return;
                    }
                    String activityId = messageObj.getString("activityId");
                    if (corgiUtilService.lock("view_" + activityId)) {
                        try {
                            CorgiFeed feed = new CorgiFeed();
                            feed.setUserId(user.getUserId());
                            feed.setFeed(activityId);
                            corgiFeedService.viewFeed(feed);
                        } finally {
                            corgiUtilService.unlock("view_" + activityId);
                        }
                    }
                    break;
                case "position":
                    HashMap result = this.updateUserPosition(this.buildUserPosition(messageObj), jwt);
                    this.sendMessage(JSONObject.toJSONString(result), session);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            this.closeSession(session, CloseReason.CloseCodes.UNEXPECTED_CONDITION, e.getMessage());
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("发生错误" + error.getMessage(), error);
    }

    /**
     * 服务端发送消息给客户端
     */
    private void sendMessage(String message, Session toSession) {
        try {
            log.info("服务端给客户端[{}]发送消息{}", toSession.getId(), message);
            toSession.getBasicRemote().sendText(message);
        } catch (Exception e) {
            log.error("服务端发送消息给客户端失败：{}", e);
        }
    }

    private JwtUser verifyToken(String jwt, Session session) {
        if (StringUtils.isEmpty(jwt)) {
            this.closeSession(session, CloseReason.CloseCodes.PROTOCOL_ERROR, "token不正确");
            return null;
        }
        DecodedJWT decodedJWT;
        try {
            decodedJWT = JWTUtils.verifyToken(jwt);
        } catch (Exception e) {
            this.closeSession(session, CloseReason.CloseCodes.PROTOCOL_ERROR, e.getMessage());
            return null;
        }
        return JwtUser.builder()
                .userId(decodedJWT.getClaim(JwtUser.USER_ID).asString())
                .version(decodedJWT.getClaim(JwtUser.VERSION).asString())
                .build();
    }

    private void closeSession(Session session, CloseReason.CloseCode closeCode, String reason) {
        try {
            session.close(new CloseReason(closeCode, reason));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private UserPosition buildUserPosition(JSONObject messageObj) {
        UserPosition userPosition = new UserPosition();
        userPosition.setLng(messageObj.getDouble("lng"));
        userPosition.setLat(messageObj.getDouble("lat"));
        userPosition.setRealLng(messageObj.getDouble("realLng"));
        userPosition.setRealLat(messageObj.getDouble("realLat"));
        userPosition.setCity(messageObj.getString("city"));
        userPosition.setProvince(messageObj.getString("province"));
        userPosition.setLocateType(messageObj.getString("locateType"));

        userPosition.setUserId(messageObj.getString("userId"));
        userPosition.setVersion(messageObj.getString("version"));
        userPosition.setUserId(messageObj.getString("userId"));
        return userPosition;
    }

    private HashMap updateUserPosition(UserPosition userPosition, String jwt) {
        if (userPosition.getLat() == null) {
            userPosition.setLat(1000.0);
        }
        if (userPosition.getLng() == null) {
            userPosition.setLng(1000.0);
        }
        HashMap jsonResult = new HashMap();
        jsonResult.put("code", 0);
        HashMap result = new HashMap();
        jsonResult.put("data", result);
        try {
            if (!com.alibaba.dubbo.common.utils.StringUtils.isEmpty(jwt)) {
                DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
                String jwtUserId = decodedJWT.getClaim("userId").asString();
                log.info("updating user:{} ", jwtUserId);
                if (JWTUtils.ADMIN_ID.equals(jwtUserId)) {
                    result.put("jwt", JWTUtils.createJWT(userPosition.getUserId(), userPosition.getVersion()));
                } else if (!userPosition.getUserId().equals(jwtUserId)) {
                    jsonResult.put("code", Constants.PERMISSION_ERROR_CODE);
                    jsonResult.put("message", "非当前用户");
                    return jsonResult;
                } else {
                    UserLogin u = corgiUserService.getUserLogin(jwtUserId);
                    if (u == null || com.alibaba.dubbo.common.utils.StringUtils.isEmpty(u.getUserId())) {
                        jsonResult.put("code", Constants.PERMISSION_ERROR_CODE);
                        jsonResult.put("message", "用户不存在:" + jwtUserId + " v:" + userPosition.getVersion());
                        return jsonResult;
                    }
                    Date expireDate = decodedJWT.getExpiresAt();
                    if (expireDate.getTime() - System.currentTimeMillis() < JWTUtils.expireTime) {
                        result.put("jwt", JWTUtils.createJWT(jwtUserId, userPosition.getVersion()));
                    }
                }
                if (userPosition.getLat() < 200 && userPosition.getLng() < 200) {
                    HashMap extra = new HashMap();
                    extra.put("lat", userPosition.getLat());
                    extra.put("lng", userPosition.getLng());
                    extra.put("type", PushMessage.MATCH_90_MESSAGE_TYPE);
                    extra.put("userId", userPosition.getUserId());
                    mqService.sendMessage(PushMessage.builder()
                            .type(PushMessage.MATCH)
                            .message(PushMessage.MATCH_90_MESSAGE)
                            .sourceUserId(userPosition.getUserId())
                            .extra(extra)
                            .build());
                }
            } else {
                jsonResult.put("code", Constants.PERMISSION_ERROR_CODE);
                jsonResult.put("message", "token不存在:" + userPosition.getUserId() + " v:" + userPosition.getVersion());
                return jsonResult;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            jsonResult.put("code", Constants.SERVER_ERROR_CODE);
            jsonResult.put("message", e.getMessage());
            return jsonResult;
        }
        UserPosition oldPosition = corgiUserService.getUserPosition(userPosition.getUserId());
        if (oldPosition != null) {
            result.put("city", oldPosition.getCity());
            corgiUserService.updateUserPosition(userPosition);
        } else {
            corgiUserService.updateUserPosition(userPosition);
        }
        String expireDate = corgiUserService.getUserVipExpire(userPosition.getUserId());
        CorgiUserVipDetail detail = new CorgiUserVipDetail();
        if (com.alibaba.dubbo.common.utils.StringUtils.isNotEmpty(expireDate) && !"-".equals(expireDate)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try {
                Date date = sdf.parse(expireDate);
                detail.setRemainDate(((date.getTime() - new Date().getTime()) / (1000 * 3600 * 24)));
                detail.setExpireDate(expireDate);
            } catch (Exception e) {

            }
        }
        result.put("freq", 1);
        result.put("remainDate", detail.getRemainDate());
        result.put("expireDate", detail.getExpireDate());
        return jsonResult;
    }
}
