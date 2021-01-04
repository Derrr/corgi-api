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
                    new UserQuestion().setQuestion("基友聚会，你会是动介绍大家相互认识吗？").addOption("E", "是").addOption("I", "不是"),
                    new UserQuestion().setQuestion("如果玩桌游游戏，你是一直闷不吭声，发言简短的小透明吗？").addOption("E", "是").addOption("I", "不是"),
                    new UserQuestion().setQuestion("在聚会上看到有“菜”靠近时，你会？").addOption("E", "勾搭他").addOption("I", "等勾搭"));

    private static List<UserQuestion> SN_QUESTIONS =
            Arrays.asList(
                    new UserQuestion().setQuestion("对于“做AI”的姿势及玩具方面，你属于哪一派？").addOption("S", "经典姿势保守派").addOption("N", "开拓创新挑战派"),
                    new UserQuestion().setQuestion("好基友们都是怎么评价你的？").addOption("S", "较真务实像个壹").addOption("N", "诡计多端小机零"),
                    new UserQuestion().setQuestion("哪一款菜会更吸引你？").addOption("S", "幽默可爱的").addOption("N", "成熟老练的"));

    private static List<UserQuestion> TF_QUESTIONS =
            Arrays.asList(
                    new UserQuestion().setQuestion("你觉得自己是个怎样的人").addOption("T", "理智的").addOption("F", "感性的"),
                    new UserQuestion().setQuestion("你喜欢怎样的基友").addOption("T", "言语尖锐、但合乎逻辑").addOption("F", "天性淳良、但逻辑性差"),
                    new UserQuestion().setQuestion("做决定时，你认为比较重要的是").addOption("T", "根据事实衡量").addOption("F", "考虑他人的感受和意见"));

    private static List<UserQuestion> JP_QUESTIONS =
            Arrays.asList(
                    new UserQuestion().setQuestion("和男神约会，你会？").addOption("J", "提前安排好“节目”").addOption("P", "随性看，想到什么就干什么"),
                    new UserQuestion().setQuestion("如果要组织一个基友派对，你会？").addOption("J", "把活动细节考虑细致到统一着装").addOption("P", "人来就好，外卖搞定一切"),
                    new UserQuestion().setQuestion("和基友们出去旅游，你是大总管吗？").addOption("J", "是").addOption("P", "不是"));

    private static List<UserQuestion> FACTOR1_QUESTIONS =
            Arrays.asList(
                    new UserQuestion().setQuestion("你喜欢狗还是猫？").addOption("1", "猫").addOption("2", "狗"),
                    new UserQuestion().setQuestion("手机游戏和健身，你更喜欢哪个？").addOption("3", "游戏").addOption("4", "健身"));

    private static List<UserQuestion> FACTOR2_QUESTIONS =
            Arrays.asList(
                    new UserQuestion().setQuestion("找男朋友的话，你喜欢对方？").addOption("5", "比我高").addOption("6", "比我矮"),
                    new UserQuestion().setQuestion("你更喜欢的体型？").addOption("7", "肉壮").addOption("8", "精瘦"));

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
