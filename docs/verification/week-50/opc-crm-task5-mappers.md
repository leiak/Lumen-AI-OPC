# Task 5: opc-crm mappers + XML

Date: 2026-09-11

## Files Created (14)

- `config/MybatisConfig.java` — `@MapperScan("com.ruoyi.opc.crm.mapper")`
- `mapper/CrmCustomerMapper.java` + `.xml`
- `mapper/CrmContactMapper.java` + `.xml`
- `mapper/CrmFollowUpMapper.java` + `.xml`
- `mapper/CrmOpportunityMapper.java` + `.xml`
- `mapper/CrmContractMapper.java` + `.xml`
- `mapper/CrmOrderMapper.java` + `.xml`
- `test/.../CrmMapperXmlParseTest.java` (extra validation, see below)

## Verification

- Compile: **pass** (`mvn -pl ruoyi-modules/opc-crm compile`)
- XML well-formedness: 6/6 pass
- **MyBatis parse + binding test: pass** — `CrmMapperXmlParseTest` builds a real
  `org.apache.ibatis.session.Configuration`, runs `XMLMapperBuilder.parse()` on all 6
  XMLs, then reflects over every declared method of all 6 interfaces and asserts
  `configuration.hasStatement(...)`. This catches namespace typos, missing statement
  ids, and bad `<include refid>` — which plain XML syntax checking does not.

## Design notes

- All mappers use vanilla MyBatis (NOT mybatis-plus), per opc-notification pattern.
- XMLs use `resultMap` + `<sql id="BaseColumns">` + `useGeneratedKeys="true" keyProperty="id"`.
- `<where>` + `<if>` for optional filters; `<foreach>` for `sumAmountByOwnerAndStages`.
- Soft delete: all `selectXxx` filter `deleted = 0`; `deleteById` on Contact/FollowUp is an
  `UPDATE ... SET deleted = 1` (not a physical DELETE).
- Custom queries: `selectList`, `countByOwner`, `countByOwnerAndLevel`, `selectByCustomerId`,
  `unsetPrimaryForCustomer`, `setPrimary`, `selectByOwnerId`, `selectUpcoming`, `selectByStage`,
  `selectTopByOwner`, `countByStage`, `sumAmountByOwnerAndStages`, `selectByContractId`,
  `countByContractNo`, `countByOrderNo`.

## Deviations from the plan

1. **Insert column list.** The plan's `CrmCustomerMapper.xml` reuses `<include refid="BaseColumns"/>`
   (15 columns, including `id`) in the `INSERT` but supplies only 14 values — a column/value
   count mismatch that would fail at runtime. All 6 XMLs instead spell out the insert column
   list explicitly, omitting the AUTO_INCREMENT `id`.
2. **`deleted` in `updateById`.** The plan's update omits `deleted`, but Task 6's
   `should_soft_delete_by_id` performs the soft delete through `updateById`. `deleted = #{deleted}`
   was added to every `updateById` so soft delete works.
3. **`countByContractNo` / `countByOrderNo` ignore `deleted`** — they guard a DB `UNIQUE KEY`,
   which also counts soft-deleted rows, so filtering on `deleted = 0` would report a
   false "available" and then hit a duplicate-key error on insert.

## Commit

`312db03` feat(crm): Task 5 — MybatisConfig + 6 mappers + XML
