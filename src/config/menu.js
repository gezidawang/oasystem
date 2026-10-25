// 侧边栏菜单：按模块分组。permission 为「满足任一即可见」的权限码列表；
// 空数组表示所有已登录用户可见。可见性严格由后端返回的权限码驱动，杜绝越权入口。
export const MENU_GROUPS = [
  {
    group: '工作台',
    items: [
      { path: '/dashboard', title: '首页工作台', icon: 'HomeFilled', menuKey: 'dashboard', permission: [] },
      { path: '/approvals', title: '我的申请', icon: 'Tickets', menuKey: 'approval',
        permission: ['approval:submit'] },
      { path: '/approvals/audit', title: '审批中心', icon: 'Checked', menuKey: 'approval-audit',
        permission: ['approval:audit:hr', 'approval:audit:boss'] },
    ],
  },
  {
    group: '人事管理',
    items: [
      { path: '/employees', title: '员工档案', icon: 'User', menuKey: 'employee',
        permission: ['employee:read:own', 'employee:read:dept', 'employee:read:all'] },
      { path: '/attendance/checkin', title: '员工打卡', icon: 'Clock', menuKey: 'attendance-checkin',
        permission: ['attendance:check:own'] },
      { path: '/attendance', title: '考勤记录', icon: 'Calendar', menuKey: 'attendance',
        permission: ['attendance:read:own', 'attendance:read:dept', 'attendance:read:all'] },
      { path: '/attendance/schedule', title: '排班设置', icon: 'Timer', menuKey: 'attendance-schedule',
        permission: ['attendance:schedule:manage'] },
    ],
  },
  {
    group: '行政办公',
    items: [
      { path: '/notices', title: '公告通知', icon: 'Bell', menuKey: 'notice', permission: ['notice:read:own'] },
      { path: '/notices/manage', title: '公告管理', icon: 'BellFilled', menuKey: 'notice-manage',
        permission: ['notice:create', 'notice:publish:dept', 'notice:submit:dept'] },
      { path: '/news', title: '新闻动态', icon: 'Document', menuKey: 'news', permission: ['news:read'] },
      { path: '/news/manage', title: '新闻管理', icon: 'EditPen', menuKey: 'news-manage',
        permission: ['news:manage'] },
      { path: '/files', title: '文件资料', icon: 'FolderOpened', menuKey: 'file',
        permission: ['file:read:own', 'file:upload:own'] },
    ],
  },
  {
    group: '财务管理',
    items: [
      { path: '/expenses', title: '我的报销', icon: 'Money', menuKey: 'expense',
        permission: ['expense:submit:own'] },
      { path: '/expenses/audit', title: '报销审核', icon: 'Stamp', menuKey: 'expense-audit',
        permission: ['expense:audit'] },
    ],
  },
  {
    group: '系统管理',
    items: [
      { path: '/system', title: '基础数据与系统', icon: 'Setting', menuKey: 'system',
        permission: ['role:manage', 'org:manage'] },
      // 编制申请（三权分立 2026-10-24）：提案方持 dept:manage:request、拍板方持 dept:manage:approve、
      // 经手方持 org:manage——三个职责主体都需要进页，菜单按"任一码"放行；
      // 按钮显隐看服务端能力位 canApprove/canExecute，真正的门槛在接口码+职级双闸门（后端唯一权威）
      { path: '/system/dept-requests', title: '编制申请', icon: 'SetUp', menuKey: 'system-dept-requests',
        permission: ['dept:manage:request', 'dept:manage:approve', 'org:manage'] },
    ],
  },
]

// 扁平列表，供路由/权限查表使用
export const MENU_FLAT = MENU_GROUPS.flatMap((g) => g.items)

// 按 menuKey 查权限码
export function permissionOf(menuKey) {
  const item = MENU_FLAT.find((m) => m.menuKey === menuKey)
  return item ? item.permission : []
}
