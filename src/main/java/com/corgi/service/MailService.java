package com.corgi.service;

import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.entity.MailMessage;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserDetail;
import com.sun.mail.util.MailSSLSocketFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class MailService {
    @Reference
    private CorgiUserService corgiUserService;

    private static String mailSMTPHost = "smtp.163.com";
    private static String mailAccount = "corgiteam_2019@163.com";
    private static String mailPassword = "dasdas11";
    private static String feedbackEmail = "customerfeedback@corgi.org.cn";
    public static final String CHECK_TASK = "【待审核】平台内容管理-";


    public void sendMail(MailMessage mailMessage) throws MessagingException, GeneralSecurityException, UnsupportedEncodingException {
        UserDetail userDetail = corgiUserService.getUserDetail(mailMessage.getUserId(), null);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String emailTitle = "【用户反馈】" + userDetail.getNickname() + "-" + sdf.format(new Date());
        Properties props = new Properties();
        // 开启debug调试
        props.setProperty("mail.debug", "true");

        // 发送服务器需要身份验证
        props.setProperty("mail.smtp.auth", "true");

        // 端口号
        props.put("mail.smtp.port", 465);

        // 设置邮件服务器主机名
        props.setProperty("mail.smtp.host", mailSMTPHost);

        // 发送邮件协议名称
        props.setProperty("mail.transport.protocol", "smtp");

        /**SSL认证，注意腾讯邮箱是基于SSL加密的，所以需要开启才可以使用**/
        MailSSLSocketFactory sf = new MailSSLSocketFactory();
        sf.setTrustAllHosts(true);

        //设置是否使用ssl安全连接（一般都使用）
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.socketFactory", sf);

        //创建会话
        Session session = Session.getInstance(props);

        //获取邮件对象
        //发送的消息，基于观察者模式进行设计的
        Message msg = new MimeMessage(session);

        //设置邮件标题
        msg.setSubject(emailTitle);

        //设置邮件内容
        //使用StringBuilder，因为StringBuilder加载速度会比String快，而且线程安全性也不错
        if (StringUtils.isEmpty(mailMessage.getCustomize())) {
            StringBuilder builder = new StringBuilder();

            builder.append("\n user id： " + mailMessage.getUserId());
            builder.append("\n 昵称： " + userDetail.getNickname());
            builder.append("\n 手机： " + userDetail.getTelNo());
            builder.append("\n 联系方式： " + mailMessage.getTelNo());

            builder.append("\n");

            builder.append("\n " + mailMessage.getContent());
            if (!CollectionUtils.isEmpty(mailMessage.getPics())) {
                builder.append("\n 图片： ");
                int i = 1;
                for (String pic : mailMessage.getPics()) {
                    builder.append("\n <a href='" + pic + "?x-oss-process=image/format,jpg'>" + i++ + ".jpg</a>");
                }
            }
            msg.setContent(builder.toString(), "text/html;charset=utf-8");
        } else {
            msg.setContent(mailMessage.getCustomize(), "text/html;charset=utf-8");
        }

        msg.setSentDate(new Date());


        //设置发件人邮箱
        // InternetAddress 的三个参数分别为: 发件人邮箱, 显示的昵称(只用于显示, 没有特别的要求), 昵称的字符集编码
        msg.setFrom(new InternetAddress(mailAccount, "基基", "UTF-8"));

        //得到邮差对象
        Transport transport = session.getTransport();

        //连接自己的邮箱账户
        //密码不是自己QQ邮箱的密码，而是在开启SMTP服务时所获取到的授权码
        //connect(host, user, password)
        transport.connect(mailSMTPHost, mailAccount, mailPassword);

        //发送邮件
        transport.sendMessage(msg, new Address[]{new InternetAddress(feedbackEmail)});
        transport.close();
    }

    public void sendCheckMessage(String type, String id) {
        try {
            if(StringUtils.isNotEmpty(type) && StringUtils.isNotEmpty(id)) {
                sendMail(MailMessage.builder().content(CHECK_TASK.concat(type).concat(id)).build());
            }
        } catch (Exception e) {
            log.error(e.getMessage().concat(": ").concat(type).concat(id), e);
        }
    }
}
