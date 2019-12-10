package com.corgi.common.util;

import com.corgi.entity.UserQuestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * @author tairanliu
 */
public class CharacterUtils {
    private static List<UserQuestion> EI_QUESTIONS =
            Arrays.asList(
                    new UserQuestion().setQuestion("姐妹聚会，你介绍大家互相认识那只花蝴蝶吗？").addOption("E", "是").addOption("I", "不是"),
                    new UserQuestion().setQuestion("玩狼人杀局，你是一直发言说“过”的无知村妇吗？").addOption("E", "是").addOption("I", "不是"),
                    new UserQuestion().setQuestion("有1时，你是勾搭他还是等他勾搭？").addOption("E", "勾搭他").addOption("I", "等勾搭"));

    private static List<UserQuestion> SN_QUESTIONS =
            Arrays.asList(
                    new UserQuestion().setQuestion("啪啪的时候，你喜欢尝试新zishi吗？").addOption("S", "不喜欢").addOption("N", "是的"),
                    new UserQuestion().setQuestion("姐妹们都是怎么评价你的？").addOption("S", "像1").addOption("N", "是1"),
                    new UserQuestion().setQuestion("哪一类美人儿更吸引你？").addOption("S", "可爱的").addOption("N", "成熟的"));

    private static List<UserQuestion> TF_QUESTIONS =
            Arrays.asList(
                    new UserQuestion().setQuestion("你觉得自己是个怎样的人").addOption("T", "理智的").addOption("F", "感性的"),
                    new UserQuestion().setQuestion("你喜欢怎样的姐妹").addOption("T", "言语尖锐、但合乎逻辑").addOption("F", "天性淳良、但逻辑性差"),
                    new UserQuestion().setQuestion("做决定时，你认为比较重要的是").addOption("T", "根据事实衡量").addOption("F", "考虑他人的感受和意见"));

    private static List<UserQuestion> JP_QUESTIONS =
            Arrays.asList(
                    new UserQuestion().setQuestion("和男神约会，你会提前安排好节目吗？").addOption("J", "提前安排好").addOption("P", "随性看节目"),
                    new UserQuestion().setQuestion("如果要组织一个姐妹爬梯，你会怎么安排").addOption("J", "把姐妹们活动流程计划好").addOption("P", "先邀请再安排"),
                    new UserQuestion().setQuestion("和老公出去旅游，你是安排行程的那个吗？").addOption("J", "是").addOption("P", "不是"));

    private static List<UserQuestion> FACTOR1_QUESTIONS =
            Arrays.asList(
                    new UserQuestion().setQuestion("你喜欢狗还是猫？").addOption("1", "猫").addOption("2", "狗"),
                    new UserQuestion().setQuestion("手机游戏和健身，你更喜欢哪个？").addOption("3", "游戏").addOption("4", "健身"));

    private static List<UserQuestion> FACTOR2_QUESTIONS =
            Arrays.asList(
                    new UserQuestion().setQuestion("相处的话，你喜欢对方比你高还是比你矮？").addOption("5", "比我高").addOption("6", "比我矮"),
                    new UserQuestion().setQuestion("你更喜欢肉壮的还是精瘦的？").addOption("7", "肉壮").addOption("8", "精瘦"));

    public static List<UserQuestion> getUserQuestions() {
        List<UserQuestion> questions = new ArrayList<>();
        Random r = new Random();

        int index = r.nextInt(3);
        questions.add(EI_QUESTIONS.get(index));

        index = r.nextInt(3);
        questions.add(SN_QUESTIONS.get(index));

        index = r.nextInt(3);
        questions.add(TF_QUESTIONS.get(index));

        index = r.nextInt(3);
        questions.add(JP_QUESTIONS.get(index));

        index = r.nextInt(2);
        questions.add(FACTOR1_QUESTIONS.get(index));

        index = r.nextInt(2);
        questions.add(FACTOR2_QUESTIONS.get(index));

        return questions;
    }
}
