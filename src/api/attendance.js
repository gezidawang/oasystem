import request from '@/utils/request'

// 考勤模块接口（request 的 baseURL 已是 /api）
// 后端统一返回 Result：{ success, code, message, data }
export const attendanceApi = {
  // ---- 员工打卡（attendance:check:own）----
  // 打卡页初始数据：{ workDate, serverTime, departmentName, workStartTime, workEndTime, record }
  today: () => request.get('/attendance/today'),
  // 上班打卡：返回 { checkInTime, status, statusText, late, message }
  checkIn: () => request.post('/attendance/check-in'),
  // 下班打卡：返回 { checkOutTime, status, statusText, message }
  checkOut: () => request.post('/attendance/check-out'),
  // 统一打卡（推荐）：未打=上班，已上班未下班=下班；返回含 punchType
  punch: () => request.post('/attendance/punch'),

  // ---- 考勤记录（attendance:read:own / read:dept / read:all）----
  // params: { deptId, keyword, startDate, endDate, status, page, size }
  // 返回 { total, page, size, records }
  records: (params) => request.get('/attendance/records', { params }),
  // 筛选下拉的部门列表：[{ id, name }]
  departments: () => request.get('/attendance/departments'),

  // ---- 排班设置（attendance:schedule:manage，仅超级管理员）----
  // 返回 [{ departmentId, departmentName, workStartTime, workEndTime, hasSchedule }]
  schedules: () => request.get('/attendance/schedules'),
  // 保存某部门排班：{ departmentId, workStartTime: 'HH:mm', workEndTime: 'HH:mm' }
  saveSchedule: (payload) => request.put('/attendance/schedule', payload),
}
