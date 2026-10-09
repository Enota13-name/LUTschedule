package cn.lut.schedule;

/** Names and appShow IDs read from the authenticated official portal on 2026-10-09. No personal data. */
final class AcademicCatalog {
    static final String[] CATEGORIES={"师生服务","学籍管理","创新创业","实践教学"};
    static final String[] SERVICE_NAMES={"体测成绩管理","课程查询","成绩查询","免听选课办理","缓考申请","学分收费管理","学业完成查询","补考办理","网上评教","辅修申请","我的考试安排","学生证明打印(兰理工)","成绩认定","我的课表"};
    static final String[] DESCRIPTIONS={"体测成绩与相关记录","课程信息查询","课程成绩查询","免听选课相关事项","缓考申请相关事项","学分收费相关事项","学业完成情况查询","补考相关事项","教学评价相关事项","辅修申请相关事项","个人考试安排查询","学生证明打印相关事项","成绩认定相关事项","查看 App 中的课表"};
    static final String[][] GROUPS={SERVICE_NAMES,{"转专业管理","学生信息变更","学籍异动应用"},{"大学生创新创业训练计划项目","大学生学科竞赛"},{"毕业设计(论文)管理","课程设计","实践学分认定","学生实习实践报名管理"}};
    private static final String[][] IDS={
        {"c24c825d59ef4c5d8f6f44f3379d74bc","a56da20636904721a488fc2e780c31ed","33289050114342f8953198fffdf6231a","4d52d643333a4b3d867d4a54a564e8c3","d2aba80404044bd2bbfef5f6c10280c9","0b25010ba54840e3be1f3f1f2ea6fa62","5655039aae484909abe8a66a025bb66d","9843162fe08a41fe9294783acae266eb","153d31f3af634b268c249310e7564fac","1ec2c2ee0b1645c8b09172800583ae8c","6daeb37a4a9849ba9fafcaecd5c05adc","69c4a97ed8b549729edd3199d9a5f313","44d8f00f5bdf4084851bc46bf42d7506","d1ef0e5802e242099837c14beb16efde"},
        {"2ee1dae896294335a5a7afac559d07b6","3b6dbde44e6a4500b0915b79e0b7bfce","0f1f0f094f6f49fe9be3e1db0ec587f9"},
        {"afb2a93d27164ec9b0d8f4ceccb51cb7","b649c0ded31d464884f9478c1e107a8a"},
        {"9700ce76d9be4d4dbbb0d16c40663300","f14b1931192c488190d2fc0478741cd6","596beef53ca345428f02e8b15e87ae0c","c4a03487714c49858aeb18bdce0d381d"}};
    static String[] services(int category){return category>=0&&category<GROUPS.length?GROUPS[category]:new String[0];}
    static String description(String name){for(int i=0;i<SERVICE_NAMES.length;i++)if(name.equals(SERVICE_NAMES[i]))return DESCRIPTIONS[i];return "打开官网 · "+name;}
    static String route(android.content.SharedPreferences prefs,String name){String saved=SiteGateway.savedPageUrl(prefs.getString("service_"+name,""));if(!saved.isEmpty())return saved;for(int g=0;g<GROUPS.length;g++)for(int i=0;i<GROUPS[g].length;i++)if(name.equals(GROUPS[g][i]))return "https://jwxt.lut.edu.cn/jwapp/sys/emaphome/appShow.do?id="+IDS[g][i];return "";}
    private AcademicCatalog(){}
}
