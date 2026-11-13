/** 由scripts/build-prototype-applications.py只读提取；来源摘要和原始矩阵保留在数据中。 */
(function () {
'use strict';
const source = {
  "sourceFile": "市政公司应用软件功能权限清单.xlsx",
  "sourceSha256": "13c18de30710cc2867588aa99843c6c388c0a8ee85029f3420f7b4c4c9fad655",
  "sheet": "市政公司应用软件功能权限清单",
  "roleNames": [
    "系统管理员",
    "公司领导",
    "部门经理",
    "项目经理",
    "普通员工"
  ],
  "systems": [
    {
      "id": 2,
      "code": "APP_01",
      "name": "OA协同办公系统",
      "sourceCell": "A3",
      "enabled": true
    },
    {
      "id": 3,
      "code": "APP_02",
      "name": "企业即时通讯系统",
      "sourceCell": "A21",
      "enabled": true
    },
    {
      "id": 4,
      "code": "APP_03",
      "name": "企业知识库管理系统",
      "sourceCell": "A33",
      "enabled": true
    },
    {
      "id": 5,
      "code": "APP_04",
      "name": "人力资源管理系统",
      "sourceCell": "A45",
      "enabled": true
    },
    {
      "id": 6,
      "code": "APP_05",
      "name": "财务管理系统",
      "sourceCell": "A66",
      "enabled": true
    },
    {
      "id": 7,
      "code": "APP_06",
      "name": "工程项目管理系统(PMIS)",
      "sourceCell": "A93",
      "enabled": true
    },
    {
      "id": 8,
      "code": "APP_07",
      "name": "招投标管理系统",
      "sourceCell": "A128",
      "enabled": true
    },
    {
      "id": 9,
      "code": "APP_08",
      "name": "物资设备管理系统",
      "sourceCell": "A146",
      "enabled": true
    },
    {
      "id": 10,
      "code": "APP_09",
      "name": "移动巡检APP",
      "sourceCell": "A170",
      "enabled": true
    },
    {
      "id": 11,
      "code": "APP_10",
      "name": "工地AI视频监控系统",
      "sourceCell": "A188",
      "enabled": true
    },
    {
      "id": 12,
      "code": "APP_11",
      "name": "GIS地下管线管理系统",
      "sourceCell": "A207",
      "enabled": true
    },
    {
      "id": 13,
      "code": "APP_12",
      "name": "企业档案管理系统",
      "sourceCell": "A226",
      "enabled": true
    },
    {
      "id": 14,
      "code": "APP_13",
      "name": "车辆管理系统",
      "sourceCell": "A244",
      "enabled": true
    },
    {
      "id": 15,
      "code": "APP_14",
      "name": "企业宣传平台",
      "sourceCell": "A262",
      "enabled": true
    },
    {
      "id": 16,
      "code": "APP_15",
      "name": "安全生产管理系统",
      "sourceCell": "A280",
      "enabled": true
    },
    {
      "id": 17,
      "code": "APP_16",
      "name": "合同全生命周期管理系统",
      "sourceCell": "A302",
      "enabled": true
    },
    {
      "id": 18,
      "code": "APP_17",
      "name": "成本管控系统",
      "sourceCell": "A323",
      "enabled": true
    },
    {
      "id": 19,
      "code": "APP_18",
      "name": "系统管理后台",
      "sourceCell": "A341",
      "enabled": true
    }
  ],
  "features": [
    {
      "row": 3,
      "systemId": 2,
      "module": "公文管理",
      "name": "收文登记与流转",
      "description": "支持外部来文登记、扫码录入、公文要素自动识别、流转路径配置",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 4,
      "systemId": 2,
      "module": "公文管理",
      "name": "发文拟稿与审核",
      "description": "支持公文模板套用、在线编辑、多人会签、分级审核、电子签章",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 5,
      "systemId": 2,
      "module": "公文管理",
      "name": "公文签发与归档",
      "description": "支持领导签发、公文套红、格式校验、自动归档、档案系统对接",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 6,
      "systemId": 2,
      "module": "公文管理",
      "name": "公文催办与督办",
      "description": "支持超时自动催办、督办任务下发、进度跟踪、逾期预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 7,
      "systemId": 2,
      "module": "公文管理",
      "name": "密级与权限管理",
      "description": "支持公文密级设置、阅文范围管控、水印加密、操作日志审计",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 8,
      "systemId": 2,
      "module": "流程审批管理",
      "name": "流程模板配置",
      "description": "支持可视化流程设计、节点配置、条件分支、会签/或签规则设置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 9,
      "systemId": 2,
      "module": "流程审批管理",
      "name": "流程发起与审批",
      "description": "支持各类办公流程发起、移动端审批、意见填写、附件上传、流程撤回",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看/新增/编辑",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2,
          3
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 10,
      "systemId": 2,
      "module": "流程审批管理",
      "name": "流程跟踪与预警",
      "description": "支持流程进度实时查看、超时预警、催办、流程转办、委托代办",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 11,
      "systemId": 2,
      "module": "流程审批管理",
      "name": "流程数据统计",
      "description": "支持流程办结率、平均办结时长、超时率等统计分析、报表导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 12,
      "systemId": 2,
      "module": "会议管理",
      "name": "会议室预约",
      "description": "支持会议室资源管理、在线预约、冲突检测、预约审批、短信通知",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 13,
      "systemId": 2,
      "module": "会议管理",
      "name": "会议通知与签到",
      "description": "支持会议通知批量发送、二维码签到、人脸识别签到、参会人员统计",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 14,
      "systemId": 2,
      "module": "会议管理",
      "name": "会议纪要管理",
      "description": "支持会议纪要在线编辑、审核、发布、归档、任务拆解与跟踪",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 15,
      "systemId": 2,
      "module": "督查督办管理",
      "name": "督办任务立项",
      "description": "支持重点工作、领导批示、会议决议督办任务立项、责任分解、时限设置",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 16,
      "systemId": 2,
      "module": "督查督办管理",
      "name": "任务进度跟踪",
      "description": "支持任务进度填报、节点验收、逾期预警、进度可视化展示",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 17,
      "systemId": 2,
      "module": "督查督办管理",
      "name": "督办考核与归档",
      "description": "支持任务完成情况考核、评分、结果归档、统计分析",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 18,
      "systemId": 2,
      "module": "印章证照管理",
      "name": "印章台账管理",
      "description": "支持公司印章、证照台账登记、使用范围设置、保管人管理",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 19,
      "systemId": 2,
      "module": "印章证照管理",
      "name": "用印申请与审批",
      "description": "支持在线用印申请、附件上传、分级审批、用印记录自动留存",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 20,
      "systemId": 2,
      "module": "印章证照管理",
      "name": "证照借阅管理",
      "description": "支持证照借阅申请、审批、归还登记、到期催还、借阅记录查询",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 21,
      "systemId": 3,
      "module": "组织架构与通讯录",
      "name": "组织架构同步",
      "description": "与HR系统组织架构实时同步、部门人员自动更新、人员状态实时展示",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 22,
      "systemId": 3,
      "module": "组织架构与通讯录",
      "name": "通讯录管理",
      "description": "支持员工通讯录、外部联系人管理、分组管理、号码隐私保护",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 23,
      "systemId": 3,
      "module": "组织架构与通讯录",
      "name": "人员状态管理",
      "description": "支持在线状态、忙碌状态、请假/出差状态设置、自动回复",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看/编辑"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 24,
      "systemId": 3,
      "module": "沟通交流管理",
      "name": "单聊/群聊管理",
      "description": "支持一对一单聊、多人群聊、群公告、群文件、群权限设置",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 25,
      "systemId": 3,
      "module": "沟通交流管理",
      "name": "消息管理",
      "description": "支持消息已读回执、消息撤回、消息收藏、保密消息、阅后即焚",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看/编辑"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 26,
      "systemId": 3,
      "module": "沟通交流管理",
      "name": "文件传输管理",
      "description": "支持大文件传输、断点续传、文件加密、有效期设置、下载权限管控",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 27,
      "systemId": 3,
      "module": "会议与协同",
      "name": "音视频会议",
      "description": "支持多人音视频会议、屏幕共享、远程协助、会议录制、字幕翻译",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 28,
      "systemId": 3,
      "module": "会议与协同",
      "name": "在线文档协同",
      "description": "支持多人在线文档编辑、实时同步、评论、@提及、版本管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 29,
      "systemId": 3,
      "module": "会议与协同",
      "name": "待办消息推送",
      "description": "与OA、项目、财务系统待办消息集成、统一推送、移动端处理",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 30,
      "systemId": 3,
      "module": "移动端办公",
      "name": "移动审批",
      "description": "支持各类业务流程移动端审批、意见填写、附件查看、手写签名",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看/审批",
        "查看"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 31,
      "systemId": 3,
      "module": "移动端办公",
      "name": "定位与打卡",
      "description": "支持GPS定位打卡、电子围栏、外勤签到、打卡记录统计",
      "roleRights": [
        "全部",
        "查看",
        "查看/导出",
        "查看",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "导出",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 32,
      "systemId": 3,
      "module": "移动端办公",
      "name": "离线消息同步",
      "description": "支持离线消息缓存、网络恢复后自动同步、离线文件传输",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 33,
      "systemId": 4,
      "module": "知识体系管理",
      "name": "知识分类体系配置",
      "description": "支持多级知识分类、标签管理、知识密级设置、分类权限管控",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 34,
      "systemId": 4,
      "module": "知识体系管理",
      "name": "知识模板管理",
      "description": "支持文档模板、规范模板、报告模板上传、编辑、套用",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 35,
      "systemId": 4,
      "module": "知识体系管理",
      "name": "专家库管理",
      "description": "支持行业专家、技术专家台账管理、专业领域标注、咨询对接",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 36,
      "systemId": 4,
      "module": "知识文档管理",
      "name": "文档上传与编辑",
      "description": "支持多格式文档上传、在线编辑、版本管理、权限设置、水印加密",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增/编辑",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2,
          3
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 37,
      "systemId": 4,
      "module": "知识文档管理",
      "name": "文档审核与发布",
      "description": "支持文档多级审核、发布流程、有效期设置、过期自动下架",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 38,
      "systemId": 4,
      "module": "知识文档管理",
      "name": "文档归档与销毁",
      "description": "支持文档归档、密级调整、到期鉴定、销毁流程、操作日志留存",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 39,
      "systemId": 4,
      "module": "知识检索与应用",
      "name": "全文检索",
      "description": "支持标题、正文、附件全文检索、关键词高亮、检索结果排序",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 40,
      "systemId": 4,
      "module": "知识检索与应用",
      "name": "知识分享与互动",
      "description": "支持知识点赞、收藏、评论、分享、问答社区、问题悬赏",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 41,
      "systemId": 4,
      "module": "知识检索与应用",
      "name": "知识统计分析",
      "description": "支持知识阅读量、下载量、点赞量统计、热门知识排行、知识贡献统计",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 42,
      "systemId": 4,
      "module": "专业知识库",
      "name": "工程规范库",
      "description": "支持市政工程行业规范、标准、图集、法规文件管理、版本更新",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 43,
      "systemId": 4,
      "module": "专业知识库",
      "name": "项目复盘库",
      "description": "支持已完项目复盘报告、经验总结、问题案例、优秀方案归档",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 44,
      "systemId": 4,
      "module": "专业知识库",
      "name": "政策法规库",
      "description": "支持国家、地方市政行业政策、法规、文件更新、解读、查询",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 45,
      "systemId": 5,
      "module": "组织架构管理",
      "name": "组织架构配置",
      "description": "支持公司、分公司、部门、项目部多级组织架构设置、层级调整",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 46,
      "systemId": 5,
      "module": "组织架构管理",
      "name": "岗位管理",
      "description": "支持岗位体系设置、岗位说明书编制、岗位权责配置、人员编制管控",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 47,
      "systemId": 5,
      "module": "组织架构管理",
      "name": "人员编制管理",
      "description": "支持各部门、项目部编制核定、编制使用情况监控、超编预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 48,
      "systemId": 5,
      "module": "员工信息管理",
      "name": "员工台账管理",
      "description": "支持员工基本信息、入职、合同、考勤、薪酬、奖惩全生命周期档案管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "查看/编辑本人"
      ],
      "operations": [
        "查看",
        "编辑",
        "编辑本人",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 49,
      "systemId": 5,
      "module": "员工信息管理",
      "name": "员工异动管理",
      "description": "支持员工转正、调岗、晋升、降职、借调、离职全流程管理",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 50,
      "systemId": 5,
      "module": "员工信息管理",
      "name": "人事档案管理",
      "description": "支持员工电子档案上传、归档、借阅、归还、到期提醒、档案合规检查",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 51,
      "systemId": 5,
      "module": "招聘与入职管理",
      "name": "招聘需求管理",
      "description": "支持招聘需求提报、审批、发布、渠道管理、简历收集",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 52,
      "systemId": 5,
      "module": "招聘与入职管理",
      "name": "招聘流程管理",
      "description": "支持简历筛选、面试安排、面试评价、offer发放、入职办理全流程管控",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 53,
      "systemId": 5,
      "module": "招聘与入职管理",
      "name": "入职管理",
      "description": "支持新员工入职手续办理、资料收集、合同签订、试用期管理、转正考核",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 54,
      "systemId": 5,
      "module": "考勤与休假管理",
      "name": "考勤管理",
      "description": "支持打卡数据同步、考勤异常处理、请假/加班/出差管理、考勤统计",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "查看/编辑本人"
      ],
      "operations": [
        "查看",
        "编辑",
        "编辑本人",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 55,
      "systemId": 5,
      "module": "考勤与休假管理",
      "name": "休假管理",
      "description": "支持年假、事假、病假、婚假等各类假期额度管理、请假审批、假期消耗统计",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 56,
      "systemId": 5,
      "module": "考勤与休假管理",
      "name": "加班管理",
      "description": "支持加班申请、审批、加班时长统计、加班调休管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 57,
      "systemId": 5,
      "module": "薪酬福利管理",
      "name": "薪酬核算",
      "description": "支持薪资体系设置、考勤数据联动、社保公积金核算、个税计算、工资条生成",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "查看本人"
      ],
      "operations": [
        "查看",
        "查看本人",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 58,
      "systemId": 5,
      "module": "薪酬福利管理",
      "name": "福利管理",
      "description": "支持社保、公积金、商业保险、节日福利、体检管理、福利额度管控",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "查看本人"
      ],
      "operations": [
        "查看",
        "查看本人",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 59,
      "systemId": 5,
      "module": "薪酬福利管理",
      "name": "成本分摊",
      "description": "支持人工成本按部门、项目分摊、人工成本统计分析、报表导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 60,
      "systemId": 5,
      "module": "绩效管理",
      "name": "考核体系设置",
      "description": "支持考核指标、考核周期、考核流程、评分标准配置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 61,
      "systemId": 5,
      "module": "绩效管理",
      "name": "考核流程管理",
      "description": "支持考核目标制定、过程跟踪、自评、上级评价、结果审核、申诉处理",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 62,
      "systemId": 5,
      "module": "绩效管理",
      "name": "考核结果应用",
      "description": "支持考核结果与薪酬、晋升、培训联动、结果统计分析、归档",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "无",
        "查看本人"
      ],
      "operations": [
        "查看",
        "查看本人",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 63,
      "systemId": 5,
      "module": "培训与发展管理",
      "name": "培训计划管理",
      "description": "支持培训需求调研、培训计划制定、审批、培训资源管理",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 64,
      "systemId": 5,
      "module": "培训与发展管理",
      "name": "培训实施管理",
      "description": "支持培训报名、签到、课程管理、考试考核、培训档案记录",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 65,
      "systemId": 5,
      "module": "培训与发展管理",
      "name": "职业发展管理",
      "description": "支持员工职业发展路径规划、职称管理、技能等级管理、人才梯队建设",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "查看/编辑本人"
      ],
      "operations": [
        "查看",
        "编辑",
        "编辑本人",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 66,
      "systemId": 6,
      "module": "总账管理",
      "name": "凭证管理",
      "description": "支持凭证录入、审核、过账、反过账、凭证模板、批量导入",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 67,
      "systemId": 6,
      "module": "总账管理",
      "name": "账簿管理",
      "description": "支持总账、明细账、日记账、多栏账查询、打印、导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 68,
      "systemId": 6,
      "module": "总账管理",
      "name": "期末处理",
      "description": "支持损益结转、期末调汇、结转损益、结账、反结账",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 69,
      "systemId": 6,
      "module": "应收应付管理",
      "name": "应收账款管理",
      "description": "支持应收单录入、审核、回款核销、账龄分析、坏账计提、催收管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 70,
      "systemId": 6,
      "module": "应收应付管理",
      "name": "应付账款管理",
      "description": "支持应付单录入、审核、付款申请、付款核销、账龄分析、供应商对账",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 71,
      "systemId": 6,
      "module": "应收应付管理",
      "name": "票据管理",
      "description": "支持银行承兑汇票、商业汇票、支票登记、背书、贴现、到期提醒",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 72,
      "systemId": 6,
      "module": "合同结算管理",
      "name": "合同台账管理",
      "description": "支持工程合同、采购合同、服务合同台账登记、合同要素管理、到期预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 73,
      "systemId": 6,
      "module": "合同结算管理",
      "name": "结算管理",
      "description": "支持工程进度款结算、竣工结算、分包结算、采购结算、结算审核",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 74,
      "systemId": 6,
      "module": "合同结算管理",
      "name": "收款付款管理",
      "description": "支持收款计划、付款计划、收付款记录、银行流水匹配、逾期预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 75,
      "systemId": 6,
      "module": "项目成本核算",
      "name": "项目成本预算",
      "description": "支持项目成本预算编制、审核、下达、预算执行监控",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 76,
      "systemId": 6,
      "module": "项目成本核算",
      "name": "成本归集与分摊",
      "description": "支持项目直接成本、间接成本归集、成本分摊规则配置、成本核算",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 77,
      "systemId": 6,
      "module": "项目成本核算",
      "name": "成本分析与预警",
      "description": "支持项目成本构成分析、预算执行偏差分析、成本超支预警、成本报表",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 78,
      "systemId": 6,
      "module": "预算管理",
      "name": "预算编制",
      "description": "支持年度预算、项目预算编制、审核、汇总、下达",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 79,
      "systemId": 6,
      "module": "预算管理",
      "name": "预算执行控制",
      "description": "支持预算执行实时监控、超预算预警、预算调整申请、审批",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 80,
      "systemId": 6,
      "module": "预算管理",
      "name": "预算分析考核",
      "description": "支持预算执行情况分析、偏差分析、考核评价、报表导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 81,
      "systemId": 6,
      "module": "资金管理",
      "name": "银行账户管理",
      "description": "支持银行账户台账、账户收支明细、余额监控、账户权限管控",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 82,
      "systemId": 6,
      "module": "资金管理",
      "name": "资金计划管理",
      "description": "支持资金收支计划、资金缺口预测、资金调度、融资管理",
      "roleRights": [
        "全部",
        "查看/审批",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 83,
      "systemId": 6,
      "module": "资金管理",
      "name": "现金流管理",
      "description": "支持现金流实时监控、现金流预测、资金风险预警、现金流报表",
      "roleRights": [
        "全部",
        "查看/导出",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 84,
      "systemId": 6,
      "module": "资产管理",
      "name": "固定资产管理",
      "description": "支持固定资产台账、折旧计提、资产变动、盘点、处置全生命周期管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 85,
      "systemId": 6,
      "module": "资产管理",
      "name": "无形资产管理",
      "description": "支持无形资产台账、摊销、变动、处置管理",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 86,
      "systemId": 6,
      "module": "资产管理",
      "name": "资产盘点管理",
      "description": "支持盘点计划制定、盘点执行、盘点差异处理、盘盈盘亏处理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 87,
      "systemId": 6,
      "module": "税务管理",
      "name": "税务核算",
      "description": "支持增值税、企业所得税等税种核算、纳税申报表生成、申报管理",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 88,
      "systemId": 6,
      "module": "税务管理",
      "name": "发票管理",
      "description": "支持进项发票、销项发票管理、发票认证、发票开具、发票台账",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 89,
      "systemId": 6,
      "module": "税务管理",
      "name": "税务风险管控",
      "description": "支持税务风险识别、预警、税务合规检查、税务筹划管理",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 90,
      "systemId": 6,
      "module": "财务报表",
      "name": "财务报表生成",
      "description": "支持资产负债表、利润表、现金流量表等标准财务报表自动生成",
      "roleRights": [
        "全部",
        "查看/导出",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 91,
      "systemId": 6,
      "module": "财务报表",
      "name": "管理报表",
      "description": "支持项目成本报表、部门费用报表、资金报表等自定义管理报表",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 92,
      "systemId": 6,
      "module": "财务报表",
      "name": "合并报表",
      "description": "支持分公司、子公司报表合并、抵消分录自动生成、合并报表出具",
      "roleRights": [
        "全部",
        "查看/导出",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 93,
      "systemId": 7,
      "module": "项目立项管理",
      "name": "项目立项申请",
      "description": "支持项目立项申请、可行性研究报告上传、项目基本信息登记",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 94,
      "systemId": 7,
      "module": "项目立项管理",
      "name": "项目立项审批",
      "description": "支持项目分级审批、立项批复、项目编号生成、立项档案归档",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/审批",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 95,
      "systemId": 7,
      "module": "项目立项管理",
      "name": "项目台账管理",
      "description": "支持全公司项目台账、项目状态跟踪、项目分类查询、项目统计",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 96,
      "systemId": 7,
      "module": "项目策划与报批",
      "name": "项目策划管理",
      "description": "支持项目管理策划、施工组织设计、专项方案编制、审核、归档",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增/编辑",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2,
          3
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 97,
      "systemId": 7,
      "module": "项目策划与报批",
      "name": "报批报建管理",
      "description": "支持项目立项、用地、规划、施工许可等报批报建事项跟踪、资料管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 98,
      "systemId": 7,
      "module": "项目策划与报批",
      "name": "专项方案管理",
      "description": "支持危大工程专项方案、专家论证方案编制、审核、论证、归档",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 99,
      "systemId": 7,
      "module": "合同管理",
      "name": "合同台账管理",
      "description": "支持项目主合同、分包合同、采购合同、租赁合同台账管理、合同要素管控",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 100,
      "systemId": 7,
      "module": "合同管理",
      "name": "合同履约管理",
      "description": "支持合同履约节点跟踪、履约风险预警、履约记录管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 101,
      "systemId": 7,
      "module": "合同管理",
      "name": "合同变更管理",
      "description": "支持合同变更申请、审核、审批、变更协议管理、变更台账",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 102,
      "systemId": 7,
      "module": "进度管理",
      "name": "WBS任务分解",
      "description": "支持项目WBS任务分解、层级设置、任务责任人、工期设置",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增/编辑",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2,
          3
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 103,
      "systemId": 7,
      "module": "进度管理",
      "name": "进度计划编制",
      "description": "支持横道图、网络图编制、关键路径识别、计划版本管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 104,
      "systemId": 7,
      "module": "进度管理",
      "name": "进度填报与跟踪",
      "description": "支持任务进度填报、实际进度与计划对比、进度偏差分析",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 105,
      "systemId": 7,
      "module": "进度管理",
      "name": "进度预警与调整",
      "description": "支持进度滞后预警、工期索赔管理、进度计划调整、审批",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 106,
      "systemId": 7,
      "module": "质量管理",
      "name": "质量标准库管理",
      "description": "支持市政工程质量验收标准、规范、图集库管理、版本更新",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 107,
      "systemId": 7,
      "module": "质量管理",
      "name": "质量验收管理",
      "description": "支持分部分项工程验收、隐蔽工程验收、验收记录上传、验收审批",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 108,
      "systemId": 7,
      "module": "质量管理",
      "name": "质量巡检与整改",
      "description": "支持质量巡检、问题上报、整改下发、整改复核、闭环管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 109,
      "systemId": 7,
      "module": "质量管理",
      "name": "质量事故管理",
      "description": "支持质量事故上报、调查、处理、方案审批、复盘归档",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 110,
      "systemId": 7,
      "module": "安全管理",
      "name": "安全体系管理",
      "description": "支持安全生产责任制、安全管理制度、安全操作规程管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 111,
      "systemId": 7,
      "module": "安全管理",
      "name": "安全巡检与隐患管理",
      "description": "支持安全巡检、隐患上报、整改下发、复查闭环、隐患台账管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 112,
      "systemId": 7,
      "module": "安全管理",
      "name": "危大工程管理",
      "description": "支持危大工程辨识、方案编制、专家论证、过程管控、验收管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 113,
      "systemId": 7,
      "module": "安全管理",
      "name": "安全事故管理",
      "description": "支持安全事故上报、调查、处理、统计分析、复盘归档",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 114,
      "systemId": 7,
      "module": "安全管理",
      "name": "安全教育培训",
      "description": "支持安全教育计划、培训记录、考试考核、特种作业人员管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 115,
      "systemId": 7,
      "module": "成本管理",
      "name": "项目成本预算",
      "description": "支持项目中标成本、责任成本、计划成本编制、审核、下达",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 116,
      "systemId": 7,
      "module": "成本管理",
      "name": "成本动态管控",
      "description": "支持项目实际成本归集、预算执行对比、成本偏差分析、超支预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 117,
      "systemId": 7,
      "module": "成本管理",
      "name": "签证变更管理",
      "description": "支持现场签证、设计变更申请、审核、审批、成本核算",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "view/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 118,
      "systemId": 7,
      "module": "成本管理",
      "name": "成本分析与考核",
      "description": "支持项目成本核算、盈利分析、成本考核、报表导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 119,
      "systemId": 7,
      "module": "分包管理",
      "name": "分包商管理",
      "description": "支持分包商准入、资质审核、评价、分级、黑名单管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 120,
      "systemId": 7,
      "module": "分包管理",
      "name": "分包合同管理",
      "description": "支持分包合同签订、履约跟踪、结算管理、付款审核",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 121,
      "systemId": 7,
      "module": "分包管理",
      "name": "分包现场管理",
      "description": "支持分包人员管理、进度、质量、安全管控、分包考核",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 122,
      "systemId": 7,
      "module": "竣工管理",
      "name": "竣工资料管理",
      "description": "支持竣工资料编制、审核、组卷、归档、验收",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 123,
      "systemId": 7,
      "module": "竣工管理",
      "name": "竣工验收管理",
      "description": "支持竣工预验收、正式验收、验收报告、备案管理",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 124,
      "systemId": 7,
      "module": "竣工管理",
      "name": "竣工决算管理",
      "description": "支持竣工决算编制、审核、审计、项目成本最终核算",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 125,
      "systemId": 7,
      "module": "项目看板与统计",
      "name": "项目驾驶舱",
      "description": "支持项目核心指标可视化展示、进度、质量、安全、成本动态监控",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 126,
      "systemId": 7,
      "module": "项目看板与统计",
      "name": "项目统计分析",
      "description": "支持项目数量、规模、类型、状态、产值、利润等多维度统计分析",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 127,
      "systemId": 7,
      "module": "项目看板与统计",
      "name": "项目报表管理",
      "description": "支持项目周报、月报、季报、年报自动生成、审核、导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/编辑/导出",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 128,
      "systemId": 8,
      "module": "招标计划管理",
      "name": "招标计划编制",
      "description": "支持年度招标计划、项目招标计划编制、审核、下达",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 129,
      "systemId": 8,
      "module": "招标计划管理",
      "name": "招标计划调整",
      "description": "支持招标计划调整申请、审核、审批、计划执行跟踪",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 130,
      "systemId": 8,
      "module": "招标计划管理",
      "name": "招标计划统计",
      "description": "支持招标计划执行情况统计、完成率分析、报表导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 131,
      "systemId": 8,
      "module": "招标文件管理",
      "name": "招标文件编制",
      "description": "支持招标文件模板套用、在线编辑、条款配置、工程量清单编制",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 132,
      "systemId": 8,
      "module": "招标文件管理",
      "name": "招标文件审核",
      "description": "支持招标文件多级审核、合规性审查、修改意见跟踪",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 133,
      "systemId": 8,
      "module": "招标文件管理",
      "name": "招标文件发布",
      "description": "支持招标文件在线发布、投标单位报名、标书下载权限管控",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 134,
      "systemId": 8,
      "module": "投标管理",
      "name": "投标单位管理",
      "description": "支持投标单位报名、资质审核、资格预审、投标单位台账",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 135,
      "systemId": 8,
      "module": "投标管理",
      "name": "投标文件编制",
      "description": "支持投标文件模板、在线编辑、报价编制、标书自检",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 136,
      "systemId": 8,
      "module": "投标管理",
      "name": "投标文件递交",
      "description": "支持投标文件在线加密递交、截止时间管控、递交记录留存",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 137,
      "systemId": 8,
      "module": "开标评标管理",
      "name": "开标管理",
      "description": "支持在线开标、投标文件解密、唱标记录、开标过程全程留痕",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 138,
      "systemId": 8,
      "module": "开标评标管理",
      "name": "评标管理",
      "description": "支持评标专家抽取、评标标准配置、在线评标、打分汇总、评标报告生成",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 139,
      "systemId": 8,
      "module": "开标评标管理",
      "name": "中标公示管理",
      "description": "支持中标候选人公示、中标结果公告、质疑投诉处理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 140,
      "systemId": 8,
      "module": "供应商管理",
      "name": "供应商准入",
      "description": "支持供应商注册、资质上传、准入审核、供应商库分类管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 141,
      "systemId": 8,
      "module": "供应商管理",
      "name": "供应商评价",
      "description": "支持供应商履约评价、服务评价、分级管理、黑名单制度",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 142,
      "systemId": 8,
      "module": "供应商管理",
      "name": "供应商台账",
      "description": "支持供应商信息、资质、业绩、评价记录台账管理、到期预警",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 143,
      "systemId": 8,
      "module": "招投标档案管理",
      "name": "招投标档案归档",
      "description": "支持招标、投标、开标、评标、中标全流程资料自动归档",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 144,
      "systemId": 8,
      "module": "招投标档案管理",
      "name": "档案查询与借阅",
      "description": "支持招投标档案查询、借阅申请、审批、归还登记",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 145,
      "systemId": 8,
      "module": "招投标档案管理",
      "name": "合规性检查",
      "description": "支持招投标流程合规性检查、风险点识别、预警",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 146,
      "systemId": 9,
      "module": "供应商管理",
      "name": "供应商准入管理",
      "description": "支持物资、设备供应商注册、资质审核、准入审批、供应商库管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 147,
      "systemId": 9,
      "module": "供应商管理",
      "name": "供应商评价管理",
      "description": "支持供应商供货质量、及时性、服务评价、分级管理、黑名单",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 148,
      "systemId": 9,
      "module": "供应商管理",
      "name": "供应商台账管理",
      "description": "支持供应商资质、业绩、评价、供货记录台账、到期预警",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 149,
      "systemId": 9,
      "module": "物资计划管理",
      "name": "物资需求计划",
      "description": "支持项目物资需求计划编制、审核、汇总、需求平衡",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 150,
      "systemId": 9,
      "module": "物资计划管理",
      "name": "物资采购计划",
      "description": "支持采购计划编制、审核、下达、采购执行跟踪",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 151,
      "systemId": 9,
      "module": "物资计划管理",
      "name": "计划执行跟踪",
      "description": "支持物资计划执行情况跟踪、到货率、消耗率统计、偏差预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/导出",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 152,
      "systemId": 9,
      "module": "采购管理",
      "name": "采购寻源管理",
      "description": "支持招标采购、询价采购、竞争性谈判、单一来源采购全流程管理",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 153,
      "systemId": 9,
      "module": "采购管理",
      "name": "采购合同管理",
      "description": "支持采购合同签订、履约跟踪、到货验收、结算付款管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 154,
      "systemId": 9,
      "module": "采购管理",
      "name": "采购订单管理",
      "description": "支持采购订单下达、跟踪、到货确认、订单关闭",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 155,
      "systemId": 9,
      "module": "仓储管理",
      "name": "入库管理",
      "description": "支持物资到货验收、入库单生成、扫码入库、批次管理、台账更新",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 156,
      "systemId": 9,
      "module": "仓储管理",
      "name": "出库管理",
      "description": "支持物资领用申请、审核、出库单生成、扫码出库、库存扣减",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 157,
      "systemId": 9,
      "module": "仓储管理",
      "name": "库存管理",
      "description": "支持库存台账、库存查询、库存预警、安全库存设置、库存盘点",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 158,
      "systemId": 9,
      "module": "仓储管理",
      "name": "盘点管理",
      "description": "支持盘点计划制定、盘点执行、盘点差异处理、盘盈盘亏审批",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 159,
      "systemId": 9,
      "module": "物资追溯管理",
      "name": "物资批次追溯",
      "description": "支持物资从采购、入库、领用、使用全生命周期批次追溯",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 160,
      "systemId": 9,
      "module": "物资追溯管理",
      "name": "二维码管理",
      "description": "支持物资二维码生成、扫码查询、出入库扫码、现场扫码查验",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 161,
      "systemId": 9,
      "module": "物资追溯管理",
      "name": "质量追溯管理",
      "description": "支持物资质量问题追溯、责任认定、召回管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 162,
      "systemId": 9,
      "module": "设备全生命周期管理",
      "name": "设备台账管理",
      "description": "支持自有设备、租赁设备台账、设备履历、技术参数、权属管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 163,
      "systemId": 9,
      "module": "设备全生命周期管理",
      "name": "设备采购/租赁管理",
      "description": "支持设备采购、租赁申请、审核、审批、合同管理",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 164,
      "systemId": 9,
      "module": "设备全生命周期管理",
      "name": "设备使用管理",
      "description": "支持设备领用、借用、归还、使用记录、操作人员管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 165,
      "systemId": 9,
      "module": "设备全生命周期管理",
      "name": "设备维保管理",
      "description": "支持设备维保计划制定、维保记录、故障维修、配件更换管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 166,
      "systemId": 9,
      "module": "设备全生命周期管理",
      "name": "设备处置管理",
      "description": "支持设备报废、处置、转让、残值处理、台账更新",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 167,
      "systemId": 9,
      "module": "油料与耗材管理",
      "name": "油料管理",
      "description": "支持油料采购、入库、领用、库存、油耗统计、油耗定额管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 168,
      "systemId": 9,
      "module": "油料与耗材管理",
      "name": "耗材管理",
      "description": "支持劳保用品、办公耗材、工程耗材采购、领用、库存管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 169,
      "systemId": 9,
      "module": "油料与耗材管理",
      "name": "费用核算",
      "description": "支持物资、设备、油料、耗材费用按项目、部门核算、成本分摊",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 170,
      "systemId": 10,
      "module": "巡检任务管理",
      "name": "巡检计划制定",
      "description": "支持日常巡检、专项巡检、季节性巡检计划制定、审核、下达",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 171,
      "systemId": 10,
      "module": "巡检任务管理",
      "name": "巡检任务分配",
      "description": "支持巡检任务按区域、线路、人员分配、任务提醒、到期预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 172,
      "systemId": 10,
      "module": "巡检任务管理",
      "name": "巡检任务跟踪",
      "description": "支持巡检任务执行进度跟踪、完成率统计、逾期预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/导出",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 173,
      "systemId": 10,
      "module": "巡检执行管理",
      "name": "巡检路线规划",
      "description": "支持巡检路线制定、巡检点设置、路线导航、偏离预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 174,
      "systemId": 10,
      "module": "巡检执行管理",
      "name": "现场巡检执行",
      "description": "支持GPS定位签到、巡检项逐项检查、现场拍照/录像/录音、离线巡检",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 175,
      "systemId": 10,
      "module": "巡检执行管理",
      "name": "巡检记录管理",
      "description": "支持巡检记录自动生成、在线编辑、提交、归档、查询",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 176,
      "systemId": 10,
      "module": "隐患闭环管理",
      "name": "隐患上报",
      "description": "支持现场隐患拍照上报、隐患分级、隐患位置定位、附件上传",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 177,
      "systemId": 10,
      "module": "隐患闭环管理",
      "name": "隐患整改",
      "description": "支持隐患整改任务下发、整改期限设置、整改要求填写、整改跟踪",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 178,
      "systemId": 10,
      "module": "隐患闭环管理",
      "name": "隐患复核闭环",
      "description": "支持整改完成后复核、验收、闭环销项、隐患台账管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 179,
      "systemId": 10,
      "module": "现场作业管理",
      "name": "施工日志填报",
      "description": "支持移动端施工日志在线填报、天气、人员、机械、进度记录、附件上传",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 180,
      "systemId": 10,
      "module": "现场作业管理",
      "name": "现场签证管理",
      "description": "支持现场签证在线申请、拍照取证、坐标定位、审核、审批",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 181,
      "systemId": 10,
      "module": "现场作业管理",
      "name": "设计变更管理",
      "description": "支持设计变更现场接收、变更内容核对、现场执行记录",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 182,
      "systemId": 10,
      "module": "应急管理",
      "name": "应急事件上报",
      "description": "支持安全事故、质量事件、突发情况一键上报、位置定位、附件上传",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 183,
      "systemId": 10,
      "module": "应急管理",
      "name": "应急预案查询",
      "description": "支持应急预案库、应急处置流程、应急联系方式在线查询",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 184,
      "systemId": 10,
      "module": "应急管理",
      "name": "应急指挥联动",
      "description": "支持应急指令下发、人员定位、应急资源调度、现场指挥",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 185,
      "systemId": 10,
      "module": "移动端协同",
      "name": "知识库查询",
      "description": "支持工程规范、施工方案、技术标准、安全规范移动端在线查询",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 186,
      "systemId": 10,
      "module": "移动端协同",
      "name": "待办事项处理",
      "description": "支持OA、项目、安全、质量待办事项移动端处理、审批",
      "roleRights": [
        "全部",
        "查看",
        "查看/审批",
        "查看/审批",
        "查看"
      ],
      "operations": [
        "查看",
        "审批",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 187,
      "systemId": 10,
      "module": "移动端协同",
      "name": "即时通讯",
      "description": "支持项目人员即时通讯、群聊、文件传输、位置共享",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 188,
      "systemId": 11,
      "module": "视频监控管理",
      "name": "实时视频监控",
      "description": "支持工地摄像头实时视频查看、多画面分割、云台控制、焦距调整",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 189,
      "systemId": 11,
      "module": "视频监控管理",
      "name": "录像回放管理",
      "description": "支持视频录像存储、按时间/摄像头检索、回放、片段剪辑、下载",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 190,
      "systemId": 11,
      "module": "视频监控管理",
      "name": "摄像头管理",
      "description": "支持摄像头台账、位置管理、状态监控、远程配置、故障告警",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 191,
      "systemId": 11,
      "module": "AI智能识别",
      "name": "安全违规识别",
      "description": "支持未戴安全帽、未穿反光衣、烟火检测、人员闯入、违规作业AI识别",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 192,
      "systemId": 11,
      "module": "AI智能识别",
      "name": "车辆违规识别",
      "description": "支持车辆违停、渣土车未冲洗、车辆超速、人员进出车辆识别",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 193,
      "systemId": 11,
      "module": "AI智能识别",
      "name": "环境监测识别",
      "description": "支持扬尘、噪音、污水排放AI识别、超标预警",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 194,
      "systemId": 11,
      "module": "AI智能识别",
      "name": "AI识别规则配置",
      "description": "支持AI识别规则、灵敏度、告警阈值配置、识别任务管理",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 195,
      "systemId": 11,
      "module": "告警管理",
      "name": "告警事件管理",
      "description": "支持AI告警事件实时推送、告警等级划分、告警详情查看、视频联动",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 196,
      "systemId": 11,
      "module": "告警管理",
      "name": "告警闭环处理",
      "description": "支持告警事件下发、整改处理、现场复核、闭环销项、告警台账",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 197,
      "systemId": 11,
      "module": "告警管理",
      "name": "告警统计分析",
      "description": "支持告警事件数量、类型、处理率、超时率统计分析、报表导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 198,
      "systemId": 11,
      "module": "视频巡检管理",
      "name": "视频巡检计划",
      "description": "支持日常视频巡检、专项巡检计划制定、巡检点位设置",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 199,
      "systemId": 11,
      "module": "视频巡检管理",
      "name": "视频巡检执行",
      "description": "支持巡检任务自动执行、巡检视频录制、巡检记录生成、异常上报",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 200,
      "systemId": 11,
      "module": "视频巡检管理",
      "name": "巡检报告管理",
      "description": "支持视频巡检报告自动生成、审核、归档、查询",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 201,
      "systemId": 11,
      "module": "应急指挥管理",
      "name": "应急指挥调度",
      "description": "支持应急事件现场视频实时调取、指挥指令下发、人员调度",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 202,
      "systemId": 11,
      "module": "应急指挥管理",
      "name": "应急资源管理",
      "description": "支持应急物资、应急设备、应急人员台账管理、位置定位",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 203,
      "systemId": 11,
      "module": "应急指挥管理",
      "name": "应急演练管理",
      "description": "支持应急演练计划、演练记录、演练视频、演练总结归档",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 204,
      "systemId": 11,
      "module": "系统管理",
      "name": "存储管理",
      "description": "支持视频存储策略、存储容量监控、过期数据自动清理、备份管理",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 205,
      "systemId": 11,
      "module": "系统管理",
      "name": "权限管理",
      "description": "支持用户权限、摄像头查看权限、AI识别权限分级配置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 206,
      "systemId": 11,
      "module": "系统管理",
      "name": "接口集成",
      "description": "支持与项目管理、安全管理、应急管理系统对接、数据同步",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 207,
      "systemId": 12,
      "module": "管线数据管理",
      "name": "管线数据入库",
      "description": "支持给水管、排水管、燃气管、电力管、通信管等管线数据入库、编辑",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 208,
      "systemId": 12,
      "module": "管线数据管理",
      "name": "管线数据图层管理",
      "description": "支持管线图层分类、样式设置、显示控制、比例尺配置",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 209,
      "systemId": 12,
      "module": "管线数据管理",
      "name": "管线数据更新",
      "description": "支持管线竣工测量数据导入、管线迁改数据更新、历史数据回溯",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 210,
      "systemId": 12,
      "module": "管线可视化管理",
      "name": "管线地图可视化",
      "description": "支持2D/3D管线地图展示、管线属性查询、空间定位、地图缩放漫游",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "查看",
        "查看"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 211,
      "systemId": 12,
      "module": "管线可视化管理",
      "name": "管线专题图制作",
      "description": "支持管线专题图、综合管线图、专业管线图生成、导出、打印",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 212,
      "systemId": 12,
      "module": "管线可视化管理",
      "name": "管线台账管理",
      "description": "支持管线权属、材质、管径、埋深、建设年代、维护单位台账管理",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 213,
      "systemId": 12,
      "module": "管线空间分析",
      "name": "管线拓扑分析",
      "description": "支持管线连通性分析、上下游分析、管线交叉分析、埋深分析",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 214,
      "systemId": 12,
      "module": "管线空间分析",
      "name": "爆管分析",
      "description": "支持给水管爆管分析、影响范围分析、关阀方案自动生成、受影响用户统计",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 215,
      "systemId": 12,
      "module": "管线空间分析",
      "name": "拆迁分析",
      "description": "支持管线拆迁影响范围分析、拆迁工程量统计、迁改方案辅助制定",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 216,
      "systemId": 12,
      "module": "管线空间分析",
      "name": "覆土厚度分析",
      "description": "支持管线覆土厚度检测、不达标段预警、统计分析",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 217,
      "systemId": 12,
      "module": "管线巡检管理",
      "name": "巡检路线管理",
      "description": "支持管线巡检路线制定、巡检点设置、路线导航",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 218,
      "systemId": 12,
      "module": "管线巡检管理",
      "name": "巡检轨迹管理",
      "description": "支持巡检人员GPS轨迹记录、轨迹回放、巡检到位率统计",
      "roleRights": [
        "全部",
        "查看",
        "查看/导出",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 219,
      "systemId": 12,
      "module": "管线巡检管理",
      "name": "管线隐患管理",
      "description": "支持管线隐患点上报、定位、整改跟踪、闭环管理、隐患台账",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 220,
      "systemId": 12,
      "module": "管线应急处置",
      "name": "应急事件定位",
      "description": "支持管线泄漏、爆管、堵塞事件快速定位、周边管线、设施查询",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 221,
      "systemId": 12,
      "module": "管线应急处置",
      "name": "应急方案辅助",
      "description": "支持关阀方案、抢险方案辅助制定、应急资源调度",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 222,
      "systemId": 12,
      "module": "管线应急处置",
      "name": "应急处置记录",
      "description": "支持应急处置过程记录、处置结果归档、事件复盘分析",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 223,
      "systemId": 12,
      "module": "数据共享与集成",
      "name": "数据共享服务",
      "description": "支持管线数据接口服务、第三方系统调用、数据共享权限管控",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 224,
      "systemId": 12,
      "module": "数据共享与集成",
      "name": "系统集成对接",
      "description": "支持与工程项目管理、巡检管理、应急管理系统对接、数据同步",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 225,
      "systemId": 12,
      "module": "数据共享与集成",
      "name": "数据安全管理",
      "description": "支持管线数据加密、访问权限管控、操作日志审计、数据备份",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 226,
      "systemId": 13,
      "module": "档案体系管理",
      "name": "档案分类体系配置",
      "description": "支持文书、工程、财务、人事、照片、实物等档案分类、编号规则设置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 227,
      "systemId": 13,
      "module": "档案体系管理",
      "name": "档案保管期限设置",
      "description": "支持档案保管期限、密级设置、归档范围与保管期限表配置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 228,
      "systemId": 13,
      "module": "档案体系管理",
      "name": "档案模板管理",
      "description": "支持档案著录模板、归档模板、借阅单模板配置",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 229,
      "systemId": 13,
      "module": "档案归档管理",
      "name": "档案收集与著录",
      "description": "支持档案在线收集、著录信息填写、附件上传、档案编号自动生成",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 230,
      "systemId": 13,
      "module": "档案归档管理",
      "name": "档案审核与归档",
      "description": "支持档案多级审核、归档确认、入库、档案盒管理、柜位管理",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 231,
      "systemId": 13,
      "module": "档案归档管理",
      "name": "档案数字化加工",
      "description": "支持纸质档案扫描、图像处理、OCR识别、双层PDF生成、数字化归档",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 232,
      "systemId": 13,
      "module": "档案借阅管理",
      "name": "档案借阅申请",
      "description": "支持档案在线借阅申请、借阅期限设置、用途填写、附件上传",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 233,
      "systemId": 13,
      "module": "档案借阅管理",
      "name": "借阅审批管理",
      "description": "支持档案借阅分级审批、密级管控、借阅权限审核",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 234,
      "systemId": 13,
      "module": "档案借阅管理",
      "name": "借阅归还管理",
      "description": "支持档案归还登记、到期催还、续借申请、逾期预警、借阅记录台账",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 235,
      "systemId": 13,
      "module": "档案库房管理",
      "name": "库房台账管理",
      "description": "支持档案库房、档案柜、档案盒台账管理、柜位编码、库存查询",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 236,
      "systemId": 13,
      "module": "档案库房管理",
      "name": "库房环境监控",
      "description": "支持库房温湿度实时监控、超标预警、环境记录自动留存",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 237,
      "systemId": 13,
      "module": "档案库房管理",
      "name": "档案盘点管理",
      "description": "支持档案盘点计划制定、盘点执行、盘点差异处理、盘盈盘亏审批",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 238,
      "systemId": 13,
      "module": "档案鉴定与销毁",
      "name": "档案鉴定管理",
      "description": "支持到期档案鉴定、保管期限调整、密级变更、鉴定记录归档",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 239,
      "systemId": 13,
      "module": "档案鉴定与销毁",
      "name": "档案销毁管理",
      "description": "支持档案销毁申请、审核、审批、销毁执行、销毁记录留存",
      "roleRights": [
        "全部",
        "查看/审批",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 240,
      "systemId": 13,
      "module": "档案鉴定与销毁",
      "name": "档案统计分析",
      "description": "支持档案库存、归档量、借阅量、数字化率等统计分析、报表导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 241,
      "systemId": 13,
      "module": "项目档案管理",
      "name": "项目档案专项验收",
      "description": "支持市政工程项目档案专项验收资料编制、审核、验收、归档",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 242,
      "systemId": 13,
      "module": "项目档案管理",
      "name": "项目档案全流程管理",
      "description": "支持项目从立项到竣工全周期档案收集、整理、归档、移交",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 243,
      "systemId": 13,
      "module": "项目档案管理",
      "name": "档案合规检查",
      "description": "支持档案管理合规性检查、风险点识别、整改跟踪",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 244,
      "systemId": 14,
      "module": "车辆台账管理",
      "name": "车辆信息台账",
      "description": "支持公务用车、工程车辆台账登记、车辆参数、权属、使用状态管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 245,
      "systemId": 14,
      "module": "车辆台账管理",
      "name": "证件管理",
      "description": "支持车辆行驶证、道路运输证、保险单、年检记录管理、到期预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 246,
      "systemId": 14,
      "module": "车辆台账管理",
      "name": "车辆分类管理",
      "description": "支持车辆按类型、用途、所属部门、使用状态分类管理、查询统计",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 247,
      "systemId": 14,
      "module": "用车调度管理",
      "name": "用车申请管理",
      "description": "支持在线用车申请、用车事由、时间、地点、车型要求填写、附件上传",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看/新增"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3,
          4
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 248,
      "systemId": 14,
      "module": "用车调度管理",
      "name": "用车审批调度",
      "description": "支持用车申请分级审批、车辆调度、司机指派、冲突检测",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 249,
      "systemId": 14,
      "module": "用车调度管理",
      "name": "派车回车管理",
      "description": "支持派车单生成、回车登记、里程记录、油耗记录、用车评价",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 250,
      "systemId": 14,
      "module": "车辆运行管理",
      "name": "车辆定位监控",
      "description": "支持车辆实时GPS定位、行驶轨迹回放、电子围栏设置、偏离预警",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 251,
      "systemId": 14,
      "module": "车辆运行管理",
      "name": "油耗管理",
      "description": "支持车辆油耗定额管理、油耗记录、油耗统计、异常油耗预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 252,
      "systemId": 14,
      "module": "车辆运行管理",
      "name": "费用管理",
      "description": "支持车辆燃油费、过路费、停车费、洗车费登记、核算、报销管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 253,
      "systemId": 14,
      "module": "车辆维保管理",
      "name": "维保计划管理",
      "description": "支持车辆保养计划、维保周期设置、到期提醒、维保记录管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 254,
      "systemId": 14,
      "module": "车辆维保管理",
      "name": "维修管理",
      "description": "支持车辆故障报修、维修申请、审批、维修记录、配件更换管理",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 255,
      "systemId": 14,
      "module": "车辆维保管理",
      "name": "年检保险管理",
      "description": "支持车辆年检、保险办理计划、到期提醒、办理记录归档",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 256,
      "systemId": 14,
      "module": "驾驶员管理",
      "name": "驾驶员台账管理",
      "description": "支持驾驶员信息、驾驶证、从业资格证、驾龄、违章记录管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 257,
      "systemId": 14,
      "module": "驾驶员管理",
      "name": "驾驶员培训管理",
      "description": "支持驾驶员安全培训、考核、继续教育记录管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 258,
      "systemId": 14,
      "module": "驾驶员管理",
      "name": "违章事故管理",
      "description": "支持车辆违章、事故上报、处理、责任认定、记录归档",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 259,
      "systemId": 14,
      "module": "车辆处置管理",
      "name": "车辆报废管理",
      "description": "支持车辆报废申请、审核、审批、报废处置、台账更新",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 260,
      "systemId": 14,
      "module": "车辆处置管理",
      "name": "车辆转让管理",
      "description": "支持车辆转让申请、评估、审批、转让过户、台账更新",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 261,
      "systemId": 14,
      "module": "车辆处置管理",
      "name": "车辆盘点管理",
      "description": "支持车辆盘点计划、盘点执行、盘点差异处理、账实核对",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 262,
      "systemId": 15,
      "module": "内容管理",
      "name": "栏目管理",
      "description": "支持网站、公众号栏目设置、排序、权限配置、栏目模板管理",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 263,
      "systemId": 15,
      "module": "内容管理",
      "name": "文章管理",
      "description": "支持新闻、公告、企业文化、项目展示文章在线编辑、发布、下架",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 264,
      "systemId": 15,
      "module": "内容管理",
      "name": "素材管理",
      "description": "支持图片、视频、附件素材库管理、分类、上传、引用",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 265,
      "systemId": 15,
      "module": "发布审核管理",
      "name": "内容审核流程",
      "description": "支持内容发布多级审核、合规性审查、修改意见跟踪",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 266,
      "systemId": 15,
      "module": "发布审核管理",
      "name": "定时发布管理",
      "description": "支持内容定时发布、定时下架、发布计划管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 267,
      "systemId": 15,
      "module": "发布审核管理",
      "name": "发布记录管理",
      "description": "支持内容发布、修改、下架记录全流程留痕、查询",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 268,
      "systemId": 15,
      "module": "企业展示管理",
      "name": "企业信息管理",
      "description": "支持企业介绍、领导团队、资质荣誉、组织架构信息管理、更新",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 269,
      "systemId": 15,
      "module": "企业展示管理",
      "name": "项目展示管理",
      "description": "支持重点工程、标杆项目展示、项目详情、图片视频管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 270,
      "systemId": 15,
      "module": "企业展示管理",
      "name": "企业文化管理",
      "description": "支持企业文化、企业动态、员工风采、党建工作内容管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 271,
      "systemId": 15,
      "module": "互动管理",
      "name": "留言管理",
      "description": "支持网站、公众号用户留言、咨询、投诉接收、回复、处理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 272,
      "systemId": 15,
      "module": "互动管理",
      "name": "招聘管理",
      "description": "支持招聘信息发布、简历接收、应聘人员管理、招聘进度跟踪",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 273,
      "systemId": 15,
      "module": "互动管理",
      "name": "问卷调查",
      "description": "支持问卷调查、投票活动创建、发布、数据统计、结果分析",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 274,
      "systemId": 15,
      "module": "运营统计",
      "name": "访问统计",
      "description": "支持网站、公众号访问量、访客数、页面浏览量、来源统计分析",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 275,
      "systemId": 15,
      "module": "运营统计",
      "name": "内容统计",
      "description": "支持文章阅读量、点赞量、转发量、评论量统计分析",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 276,
      "systemId": 15,
      "module": "运营统计",
      "name": "运营报表",
      "description": "支持日、周、月、年运营报表自动生成、导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 277,
      "systemId": 15,
      "module": "系统管理",
      "name": "站点管理",
      "description": "支持多站点管理、站点域名、模板、SEO配置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 278,
      "systemId": 15,
      "module": "系统管理",
      "name": "权限管理",
      "description": "支持用户、角色、权限分级配置、操作日志审计",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 279,
      "systemId": 15,
      "module": "系统管理",
      "name": "安全管理",
      "description": "支持网站安全防护、漏洞扫描、恶意代码检测、备份恢复",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 280,
      "systemId": 16,
      "module": "安全体系管理",
      "name": "安全责任制管理",
      "description": "支持安全生产责任制、责任状签订、责任分解、考核管理",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 281,
      "systemId": 16,
      "module": "安全体系管理",
      "name": "安全制度管理",
      "description": "支持安全生产管理制度、操作规程、应急预案编制、审核、发布",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 282,
      "systemId": 16,
      "module": "安全体系管理",
      "name": "安全目标管理",
      "description": "支持年度安全目标、指标分解、目标考核、完成情况跟踪",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 283,
      "systemId": 16,
      "module": "安全巡检与隐患管理",
      "name": "安全巡检计划管理",
      "description": "支持日常巡检、专项巡检、季节性巡检计划制定、审核、下达",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 284,
      "systemId": 16,
      "module": "安全巡检与隐患管理",
      "name": "巡检执行管理",
      "description": "支持巡检任务执行、巡检记录填写、现场拍照、问题上报",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 285,
      "systemId": 16,
      "module": "安全巡检与隐患管理",
      "name": "隐患闭环管理",
      "description": "支持隐患上报、分级、整改下发、整改跟踪、复核验收、闭环销项",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 286,
      "systemId": 16,
      "module": "安全巡检与隐患管理",
      "name": "隐患台账管理",
      "description": "支持隐患台账、整改率、逾期率统计分析、报表导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 287,
      "systemId": 16,
      "module": "危大工程安全管理",
      "name": "危大工程辨识",
      "description": "支持危大工程清单辨识、分级、风险评估、管控措施制定",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 288,
      "systemId": 16,
      "module": "危大工程安全管理",
      "name": "专项方案管理",
      "description": "支持危大工程专项施工方案编制、审核、专家论证、审批",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 289,
      "systemId": 16,
      "module": "危大工程安全管理",
      "name": "过程管控管理",
      "description": "支持危大工程施工过程管控、旁站记录、验收管理、台账",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 290,
      "systemId": 16,
      "module": "安全教育培训管理",
      "name": "培训计划管理",
      "description": "支持年度安全教育培训计划、专项培训计划制定、审核、下达",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 291,
      "systemId": 16,
      "module": "安全教育培训管理",
      "name": "培训实施管理",
      "description": "支持培训报名、签到、课程管理、考试考核、培训记录归档",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 292,
      "systemId": 16,
      "module": "安全教育培训管理",
      "name": "特种作业人员管理",
      "description": "支持特种作业人员台账、证书管理、持证上岗、到期复审提醒",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 293,
      "systemId": 16,
      "module": "安全事故管理",
      "name": "事故上报管理",
      "description": "支持安全事故、未遂事件上报、事故分级、快报续报管理",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看/新增",
        "查看"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3,
          4
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 294,
      "systemId": 16,
      "module": "安全事故管理",
      "name": "事故调查处理",
      "description": "支持事故调查、原因分析、责任认定、处理决定、整改措施落实",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 295,
      "systemId": 16,
      "module": "安全事故管理",
      "name": "事故统计分析",
      "description": "支持事故数量、类型、等级、原因统计分析、同比环比分析",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 296,
      "systemId": 16,
      "module": "应急管理",
      "name": "应急预案管理",
      "description": "支持综合应急预案、专项应急预案编制、审核、发布、演练计划",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 297,
      "systemId": 16,
      "module": "应急管理",
      "name": "应急演练管理",
      "description": "支持应急演练计划、演练实施、演练记录、演练总结、整改跟踪",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 298,
      "systemId": 16,
      "module": "应急管理",
      "name": "应急资源管理",
      "description": "支持应急物资、应急设备、应急队伍、应急专家台账管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 299,
      "systemId": 16,
      "module": "安全考核与奖惩",
      "name": "安全考核管理",
      "description": "支持部门、项目部、人员安全绩效考核、评分、评级",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 300,
      "systemId": 16,
      "module": "安全考核与奖惩",
      "name": "安全奖惩管理",
      "description": "支持安全奖励、处罚决定、审批、执行、记录归档",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 301,
      "systemId": 16,
      "module": "安全考核与奖惩",
      "name": "安全信用管理",
      "description": "支持分包单位、供应商安全信用评价、分级、黑名单管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 302,
      "systemId": 17,
      "module": "合同范本管理",
      "name": "合同范本库管理",
      "description": "支持工程合同、采购合同、服务合同、租赁合同等范本分类管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 303,
      "systemId": 17,
      "module": "合同范本管理",
      "name": "范本条款配置",
      "description": "支持合同范本通用条款、专用条款设置、变量配置",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 304,
      "systemId": 17,
      "module": "合同范本管理",
      "name": "范本版本管理",
      "description": "支持合同范本版本更新、历史版本回溯、停用管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 305,
      "systemId": 17,
      "module": "合同签订管理",
      "name": "合同起草",
      "description": "支持合同范本套用、在线起草、条款编辑、对方信息管理",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 306,
      "systemId": 17,
      "module": "合同签订管理",
      "name": "合同审核",
      "description": "支持合同法务、业务、财务多级审核、合规性审查、修改意见跟踪",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 307,
      "systemId": 17,
      "module": "合同签订管理",
      "name": "合同签署",
      "description": "支持电子签章、手写签名、合同签署、签署记录留存",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 308,
      "systemId": 17,
      "module": "合同履约管理",
      "name": "合同履约节点管理",
      "description": "支持合同履约节点、付款节点、交付节点设置、到期提醒",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 309,
      "systemId": 17,
      "module": "合同履约管理",
      "name": "履约跟踪管理",
      "description": "支持合同履约情况跟踪、履约记录上传、履约偏差预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 310,
      "systemId": 17,
      "module": "合同履约管理",
      "name": "合同变更管理",
      "description": "支持合同变更申请、审核、审批、变更协议管理、变更台账",
      "roleRights": [
        "全部",
        "查看/审批",
        "view/编辑/审批",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 311,
      "systemId": 17,
      "module": "合同结算管理",
      "name": "结算申请管理",
      "description": "支持合同结算申请、结算资料上传、结算金额核算",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 312,
      "systemId": 17,
      "module": "合同结算管理",
      "name": "结算审核管理",
      "description": "支持结算多级审核、工程量核对、金额审核、审批",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 313,
      "systemId": 17,
      "module": "合同结算管理",
      "name": "付款管理",
      "description": "支持付款申请、付款审核、付款记录、发票管理、付款台账",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 314,
      "systemId": 17,
      "module": "合同台账管理",
      "name": "合同总台账",
      "description": "支持全公司合同台账、合同状态、金额、履约情况、分类查询",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 315,
      "systemId": 17,
      "module": "合同台账管理",
      "name": "合同分类台账",
      "description": "支持按合同类型、所属部门、项目、签订时间分类台账管理",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 316,
      "systemId": 17,
      "module": "合同台账管理",
      "name": "合同统计分析",
      "description": "支持合同数量、金额、类型、履约率、结算率统计分析",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 317,
      "systemId": 17,
      "module": "合同风险管理",
      "name": "合同风险识别",
      "description": "支持合同条款风险、履约风险、法律风险识别、风险等级评估",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 318,
      "systemId": 17,
      "module": "合同风险管理",
      "name": "风险预警管理",
      "description": "支持合同风险点预警、到期提醒、逾期预警、风险整改跟踪",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 319,
      "systemId": 17,
      "module": "合同风险管理",
      "name": "纠纷处理管理",
      "description": "支持合同纠纷上报、处理、法律诉讼、仲裁记录归档",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 320,
      "systemId": 17,
      "module": "合同归档管理",
      "name": "合同归档管理",
      "description": "支持合同签订、履约、结算、变更全流程资料自动归档",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 321,
      "systemId": 17,
      "module": "合同归档管理",
      "name": "合同借阅管理",
      "description": "支持合同借阅申请、审批、归还登记、借阅记录台账",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 322,
      "systemId": 17,
      "module": "合同归档管理",
      "name": "合同保密管理",
      "description": "支持合同密级设置、访问权限管控、水印加密、操作日志审计",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 323,
      "systemId": 18,
      "module": "成本预算管理",
      "name": "责任成本编制",
      "description": "支持项目责任成本、计划成本编制、审核、下达",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 324,
      "systemId": 18,
      "module": "成本预算管理",
      "name": "成本预算分解",
      "description": "支持成本预算按分部分项、工序、部门、责任人分解",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 325,
      "systemId": 18,
      "module": "成本预算管理",
      "name": "预算执行管控",
      "description": "支持成本预算执行实时监控、超预算预警、预算调整审批",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 326,
      "systemId": 18,
      "module": "成本归集与核算",
      "name": "直接成本归集",
      "description": "支持人工费、材料费、机械费、分包费等直接成本自动归集",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 327,
      "systemId": 18,
      "module": "成本归集与核算",
      "name": "间接成本分摊",
      "description": "支持管理费用、间接费用分摊规则配置、自动分摊",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 328,
      "systemId": 18,
      "module": "成本归集与核算",
      "name": "成本核算管理",
      "description": "支持项目成本、部门成本核算、实际成本与预算对比",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 329,
      "systemId": 18,
      "module": "过程成本管控",
      "name": "签证变更成本管控",
      "description": "支持现场签证、设计变更成本核算、预算占用、成本预警",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑/审批",
        "查看/新增",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "新增",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0,
          3
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 330,
      "systemId": 18,
      "module": "过程成本管控",
      "name": "材料成本管控",
      "description": "支持材料消耗量管控、损耗率分析、材料成本偏差预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 331,
      "systemId": 18,
      "module": "过程成本管控",
      "name": "分包成本管控",
      "description": "支持分包结算管控、分包成本与预算对比、分包超支预警",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 332,
      "systemId": 18,
      "module": "成本分析与预警",
      "name": "成本偏差分析",
      "description": "支持实际成本与预算、计划成本偏差分析、原因分析",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "查看",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2,
          3
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 333,
      "systemId": 18,
      "module": "成本分析与预警",
      "name": "成本动态分析",
      "description": "支持项目成本动态走势分析、月度成本分析、竣工成本分析",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 334,
      "systemId": 18,
      "module": "成本分析与预警",
      "name": "成本风险预警",
      "description": "支持成本超支风险、盈利风险识别、预警、整改跟踪",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 335,
      "systemId": 18,
      "module": "成本考核管理",
      "name": "成本考核体系",
      "description": "支持项目、部门、人员成本考核指标、考核标准配置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 336,
      "systemId": 18,
      "module": "成本考核管理",
      "name": "成本考核执行",
      "description": "支持成本考核数据自动提取、评分、评级、考核结果生成",
      "roleRights": [
        "全部",
        "查看/审批",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "审批",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 337,
      "systemId": 18,
      "module": "成本考核管理",
      "name": "考核结果应用",
      "description": "支持成本考核结果与薪酬、绩效、奖惩联动、归档",
      "roleRights": [
        "全部",
        "查看",
        "查看",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 338,
      "systemId": 18,
      "module": "成本报表管理",
      "name": "成本报表模板",
      "description": "支持项目成本表、部门费用表、成本分析表等模板配置",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 339,
      "systemId": 18,
      "module": "成本报表管理",
      "name": "报表自动生成",
      "description": "支持成本报表按日、周、月、季、年自动生成、审核、导出",
      "roleRights": [
        "全部",
        "查看/导出",
        "查看/编辑/导出",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 340,
      "systemId": 18,
      "module": "成本报表管理",
      "name": "报表汇总分析",
      "description": "支持分公司、项目部成本报表汇总、合并、分析",
      "roleRights": [
        "全部",
        "查看/导出",
        "无",
        "无",
        null
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 341,
      "systemId": 19,
      "module": "用户与权限管理",
      "name": "用户管理",
      "description": "支持用户账号创建、编辑、停用、删除、密码重置、账号锁定",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 342,
      "systemId": 19,
      "module": "用户与权限管理",
      "name": "角色管理",
      "description": "支持角色创建、角色权限配置、角色用户分配、数据权限设置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 343,
      "systemId": 19,
      "module": "用户与权限管理",
      "name": "权限配置",
      "description": "支持功能权限、数据权限、按钮权限分级配置、权限继承设置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 344,
      "systemId": 19,
      "module": "组织架构同步",
      "name": "组织架构管理",
      "description": "支持公司、分公司、部门、项目部组织架构配置、层级调整",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 345,
      "systemId": 19,
      "module": "组织架构同步",
      "name": "人员同步管理",
      "description": "支持与HR系统人员数据同步、组织架构变更自动同步",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 346,
      "systemId": 19,
      "module": "组织架构同步",
      "name": "数据权限范围配置",
      "description": "支持按组织架构配置用户数据查看范围、数据隔离",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 347,
      "systemId": 19,
      "module": "系统配置管理",
      "name": "数据字典管理",
      "description": "支持系统下拉选项、枚举值、业务字典配置、维护",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 348,
      "systemId": 19,
      "module": "系统配置管理",
      "name": "系统参数配置",
      "description": "支持系统业务参数、流程参数、提醒参数配置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 349,
      "systemId": 19,
      "module": "系统配置管理",
      "name": "编号规则配置",
      "description": "支持单据编号、合同编号、项目编号规则配置、自动生成",
      "roleRights": [
        "全部",
        "查看",
        "查看/编辑",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "编辑",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1,
          2
        ],
        [
          0,
          2
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 350,
      "systemId": 19,
      "module": "日志审计管理",
      "name": "登录日志管理",
      "description": "支持用户登录日志、登录时间、IP地址、设备信息、登录状态记录",
      "roleRights": [
        "全部",
        "查看/导出",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 351,
      "systemId": 19,
      "module": "日志审计管理",
      "name": "操作日志管理",
      "description": "支持用户操作日志、功能访问、数据修改、操作时间记录",
      "roleRights": [
        "全部",
        "查看/导出",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "导出",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 352,
      "systemId": 19,
      "module": "日志审计管理",
      "name": "异常日志管理",
      "description": "支持系统异常、报错日志记录、监控、排查",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 353,
      "systemId": 19,
      "module": "系统监控与维护",
      "name": "系统性能监控",
      "description": "支持系统服务、CPU、内存、磁盘、接口响应时间监控",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 354,
      "systemId": 19,
      "module": "系统监控与维护",
      "name": "数据备份恢复",
      "description": "支持系统数据自动备份、手动备份、数据恢复",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 355,
      "systemId": 19,
      "module": "系统监控与维护",
      "name": "缓存管理",
      "description": "支持系统缓存配置、缓存清理、缓存性能优化",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 356,
      "systemId": 19,
      "module": "集成与接口管理",
      "name": "接口管理",
      "description": "支持系统接口配置、接口测试、接口文档、接口调用监控",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 357,
      "systemId": 19,
      "module": "集成与接口管理",
      "name": "第三方系统集成",
      "description": "支持与OA、HR、财务、项目、监控等系统集成对接",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 358,
      "systemId": 19,
      "module": "集成与接口管理",
      "name": "数据同步管理",
      "description": "支持系统间数据同步配置、同步任务管理、同步状态监控",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 359,
      "systemId": 19,
      "module": "安全管理",
      "name": "密码策略管理",
      "description": "支持密码复杂度、有效期、历史密码、登录失败锁定策略配置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 360,
      "systemId": 19,
      "module": "安全管理",
      "name": "双因素认证管理",
      "description": "支持短信、邮箱、动态令牌双因素认证配置",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    },
    {
      "row": 361,
      "systemId": 19,
      "module": "安全管理",
      "name": "数据安全管理",
      "description": "支持敏感数据加密、脱敏、访问管控、数据防泄漏",
      "roleRights": [
        "全部",
        "查看",
        "无",
        "无",
        "无"
      ],
      "operations": [
        "查看",
        "全部（原表，待接口细化）"
      ],
      "operationRoles": [
        [
          0,
          1
        ],
        [
          0
        ]
      ]
    }
  ],
  "moduleCount": 115,
  "normalizations": [
    {
      "cell": "H117",
      "source": "view",
      "display": "查看"
    },
    {
      "cell": "G310",
      "source": "view",
      "display": "查看"
    }
  ],
  "missingRights": [
    {
      "cell": "I340",
      "systemId": 18,
      "feature": "报表汇总分析",
      "role": "普通员工"
    }
  ],
  "apiMappingsProvided": false
};
if (typeof module !== 'undefined' && module.exports) module.exports = source;
else globalThis.MunicipalApplicationSource = source;
})();
