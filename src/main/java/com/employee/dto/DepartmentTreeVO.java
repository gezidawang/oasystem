package com.employee.dto;

import lombok.Data;
import java.util.List;

/** 部门树节点 VO */
@Data
public class DepartmentTreeVO {

    private Integer id;
    private String name;
    private String code;
    private Integer parentId;
    private Integer managerId;
    private String managerName;
    private Integer status;
    private List<DepartmentTreeVO> children;
}
