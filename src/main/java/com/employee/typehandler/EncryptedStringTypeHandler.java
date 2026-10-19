package com.employee.typehandler;

import com.employee.utils.AesUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 透明加密 TypeHandler（Phase 0-4）。
 * 写库时自动 AES 加密，读库时自动解密。
 * 配合 User 实体 @TableField(typeHandler=...) + @TableName(autoResultMap=true) 使用。
 */
@MappedTypes(String.class)
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, AesUtil.isInitialized() ? AesUtil.encrypt(parameter) : parameter);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return AesUtil.isInitialized() ? AesUtil.decrypt(value) : value;
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return AesUtil.isInitialized() ? AesUtil.decrypt(value) : value;
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return AesUtil.isInitialized() ? AesUtil.decrypt(value) : value;
    }
}
