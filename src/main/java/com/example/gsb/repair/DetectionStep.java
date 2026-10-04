package com.example.gsb.repair;

import java.util.List;

/** 检测步骤：检查一个目标对象，返回发现的问题列表（无问题返回空列表）。 */
public interface DetectionStep {

    String name();

    List<Issue> detect(String objectId);
}
