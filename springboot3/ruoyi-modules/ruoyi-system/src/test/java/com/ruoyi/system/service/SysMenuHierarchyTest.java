package com.ruoyi.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.system.domain.SysMenu;
import com.ruoyi.system.domain.vo.RouterVo;
import com.ruoyi.system.mapper.SysMenuMapper;
import com.ruoyi.system.service.impl.SysMenuServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@code /getRouters} 的**层级**回归测试
 *
 * <p><b>判别特征</b>：W80 P1 报的现象是「后台「菜单管理」页的树是对的,但侧边栏是一层
 * 平铺的菜单项,没有层级」。这个组合把问题锁死在 {@code getRouters} 这条渲染链上:
 *
 * <pre>
 *   SysMenuMapper.xml  selectMenuTreeAll / selectMenuTreeByUserId   (扁平 resultMap)
 *        └─ SysMenuServiceImpl#selectMenuTreeByUserId  → getChildPerms(menus, 0L)   ← 建树
 *             └─ SysMenuServiceImpl#buildMenus                                        ← 读 children 产 RouterVo
 *                  └─ 前端 store/modules/permission.ts#filterAsyncRouter             ← 只换组件,不自建树
 *                       └─ layout/components/Sidebar/SidebarItem.vue  递归渲染
 * </pre>
 *
 * <p><b>为什么 mapper 不用嵌套 resultMap</b>：{@code SysMenuResult} 里没有
 * {@code <collection property="children" .../>},所以 mapper 返回的每行 children 都停在
 * 字段初始值 {@code new ArrayList<>()}(SysMenu.java:70)。建树被<b>有意</b>放在 Java 侧
 * 的 {@code getChildPerms}(:554) —— 它按 {@code parentId == 0} 取根,对每个根调
 * {@code recursionFn}(:568) 递归填 children 再返回。mapper 保持扁平是正确取舍:
 * 一次菜单查询不该在 SQL 层递归 N+1。
 *
 * <p><b>别在这里再加一次 buildMenuTree</b>：{@code getChildPerms} 的返回值已经只剩根
 * 节点了,再套 {@code buildMenuTree}(:230) 会让 {@code recursionFn} 拿「只含根的 list」
 * 重算 children,把已填好的层级<b>清空</b>。这是本测试存在的第一道护栏
 * ({@link #doubleTreeBuildWipesChildren})。
 *
 * <p><b>顺序不可交换</b>：{@code getChildPerms} 与 {@code buildMenuTree} 都依赖
 * {@code parentId} 在完整扁平 list 上匹配,谁都不能先裁剪。
 *
 * <p>另外钉住两条<b>本 fork 与上游 RuoYi 相反</b>的约定（照抄上游必错）：
 * {@code UserConstants.NO_FRAME = "1"}(:46,上游 "0")、二级目录 component 是
 * {@code ParentView} 不是 {@code Layout}。
 *
 * @author OAC
 */
class SysMenuHierarchyTest
{
    private SysMenuServiceImpl service;

    private SysMenuMapper mapper;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp()
    {
        service = new SysMenuServiceImpl();
        mapper = Mockito.mock(SysMenuMapper.class);
        ReflectionTestUtils.setField(service, "menuMapper", mapper);
        ReflectionTestUtils.setField(service, "roleMapper", Mockito.mock(com.ruoyi.system.mapper.SysRoleMapper.class));
        ReflectionTestUtils.setField(service, "roleMenuMapper", Mockito.mock(com.ruoyi.system.mapper.SysRoleMenuMapper.class));
    }

    @AfterEach
    void tearDown()
    {
        if (securityUtils != null)
        {
            securityUtils.close();
            securityUtils = null;
        }
    }

    // ============================================================
    // 数据:与 V20261022__opc_sys_menus.sql 的 33 行同形
    // (1 个 parent_id=0 顶级目录 + 8 个二级目录 + 24 个叶子)
    // ============================================================

    /** 顶级目录 → 二级目录 → 叶子的完整三层。 */
    private static List<SysMenu> opcThreeLevelRows()
    {
        List<SysMenu> rows = new ArrayList<>();
        rows.add(dir(2000L, "OPC业务", 0L, "opc", "OpcBiz", "star"));
        rows.add(dir(2001L, "智能体市场", 2000L, "marketplace", "OpcMarketplace", "shopping"));
        rows.add(leaf(2002L, "智能体市场列表", 2001L, "index", "opc/marketplace/index", "OpcMarketplaceIndex", "list"));
        rows.add(leaf(2003L, "作者后台", 2001L, "author-center", "opc/marketplace/author-center", "OpcMarketplaceAuthorCenter", "skill"));
        return rows;
    }

    private List<RouterVo> routersFor(List<SysMenu> flatRows, long userId, boolean admin)
    {
        when(admin ? mapper.selectMenuTreeAll() : mapper.selectMenuTreeByUserId(userId))
                .thenReturn(new ArrayList<>(flatRows));
        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(() -> SecurityUtils.isAdmin(userId)).thenReturn(admin);
        return service.buildMenus(service.selectMenuTreeByUserId(userId));
    }

    private static String json(Object o)
    {
        try
        {
            return new ObjectMapper().writeValueAsString(o);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    // ============================================================
    // 缺陷本体:层级必须真的落到 RouterVo 上
    // ============================================================

    @Test
    @DisplayName("mapper 扁平返回 → getChildPerms 建树 → /getRouters 吐出 3 层嵌套")
    void getRoutersProducesThreeLevelNesting()
    {
        List<SysMenu> rows = opcThreeLevelRows();
        assertTrue(rows.get(0).getChildren().isEmpty(),
                "前提:SysMenu.children 初始就是空 list(SysMenu.java:70),mapper 不填它");

        List<RouterVo> routers = routersFor(rows, 1L, true);

        assertEquals(1, routers.size(), "顶层只应有 1 项(OPC业务),不该是 4 项平铺。" + json(routers));

        RouterVo top = routers.get(0);
        // 这 3 条正是 SidebarItem.vue:3 的 v-if 判据 —— 缺任一,菜单就只是链接而非父级
        assertTrue(top.getAlwaysShow(), "M 型目录必须 alwaysShow,否则 SidebarItem 走单子项分支:" + json(top));
        assertEquals("noRedirect", top.getRedirect());
        assertNotNull(top.getChildren());
        assertEquals(1, top.getChildren().size(), "根下应有 1 个二级目录(智能体市场):" + json(top));

        RouterVo second = top.getChildren().get(0);
        assertTrue(second.getAlwaysShow(), "二级目录同样必须 alwaysShow:" + json(second));
        assertEquals("noRedirect", second.getRedirect());
        assertEquals(2, second.getChildren().size(), "智能体市场下有 2 个叶子:" + json(second));

        RouterVo leaf = second.getChildren().get(0);
        assertEquals("opc/marketplace/index", leaf.getComponent());
        assertEquals("index", leaf.getPath(), "叶子 path 相对父目录");
        assertEquals("/opc/marketplace/index", fullPath(top, second, leaf), "拼出的完整路径");
        assertFalse(leaf.getHidden(), "visible='0' 的行不得 hidden");
        assertFalse(Boolean.TRUE.equals(leaf.getAlwaysShow()), "叶子不设 alwaysShow");
    }

    @Test
    @DisplayName("护栏:在 getChildPerms 之外再套一层 buildMenuTree 会把层级清空")
    void doubleTreeBuildWipesChildren()
    {
        // 这条不是测「我们的实现对」,而是测「错误的实现长什么样」——
        // 有人看到 mapper 返回扁平就下意识再补一次 buildMenuTree,结果更糟:
        // getChildPerms 已把 list 裁成只剩根,buildMenuTree 里的 recursionFn 拿这个
        // 裁剪后的 list 重算 children,于是 8 个二级目录的层级被抹平成空 list,
        // SidebarItem 的 hasOneShowingChild(children=[]) 返回 true,目录退化成普通链接。
        List<SysMenu> rows = opcThreeLevelRows();
        when(mapper.selectMenuTreeAll()).thenReturn(new ArrayList<>(rows));
        securityUtils = Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(() -> SecurityUtils.isAdmin(1L)).thenReturn(true);

        List<SysMenu> rootsOnly = service.selectMenuTreeByUserId(1L);
        assertEquals(1, rootsOnly.size());
        assertEquals(1, rootsOnly.get(0).getChildren().size(), "sanity: getChildPerms 本身建树正确");

        // 错误路径
        List<SysMenu> doubleBuilt = service.buildMenuTree(rootsOnly);
        assertTrue(doubleBuilt.get(0).getChildren().isEmpty(),
                "再 buildMenuTree 会清空 children —— 这正是本次差点提交的错修");
        assertTrue(service.buildMenus(doubleBuilt).get(0).getChildren() == null
                        || service.buildMenus(doubleBuilt).get(0).getChildren().isEmpty(),
                "对应到 RouterVo:顶层只剩一个可点击链接,没有任何子菜单");
    }

    @Test
    @DisplayName("非 admin 走 selectMenuTreeByUserId 分支时同样建树")
    void nonAdminBranchAlsoBuildsTree()
    {
        List<RouterVo> routers = routersFor(opcThreeLevelRows(), 2L, false);

        assertEquals(1, routers.size());
        assertEquals(1, routers.get(0).getChildren().size());
        assertEquals(2, routers.get(0).getChildren().get(0).getChildren().size());
    }

    @Test
    @DisplayName("8 个二级目录都能挂到同一个根下 —— 全量 33 行形状的建树")
    void allEightModuleDirsNestUnderOneRoot()
    {
        List<SysMenu> rows = new ArrayList<>();
        rows.add(dir(2000L, "OPC业务", 0L, "opc", "OpcBiz", "star"));
        // 8 个二级目录,各自挂 1 个叶子
        long[] dirIds = {2001L, 2005L, 2007L, 2009L, 2013L, 2017L, 2023L, 2028L};
        String[] dirNames = {"智能体市场", "模块市场", "通知中心", "客户关系", "人力资源", "进销存", "内容创作", "数据洞察"};
        for (int i = 0; i < dirIds.length; i++)
        {
            rows.add(dir(dirIds[i], dirNames[i], 2000L, "d" + i, "Dir" + i, "star"));
            rows.add(leaf(dirIds[i] + 1000L, dirNames[i] + "页", dirIds[i], "index", "opc/x/index", "Leaf" + i, "list"));
        }
        // 注意 mapper 的 SQL 有 `order by parent_id, order_num`,子行可能排在父行前面,
        // 这里刻意乱序,证明 getChildPerms 不依赖输入顺序
        rows.add(rows.remove(rows.size() - 1));

        List<RouterVo> routers = routersFor(rows, 1L, true);

        assertEquals(1, routers.size(), json(routers));
        assertEquals(8, routers.get(0).getChildren().size(), "8 个模块目录:" + json(routers));
        for (RouterVo second : routers.get(0).getChildren())
        {
            assertTrue(second.getAlwaysShow(), second.getMeta().getTitle() + " 应为可展开目录");
            assertEquals(1, second.getChildren().size(), second.getMeta().getTitle() + " 下应有 1 个叶子");
        }
    }

    // ============================================================
    // 本 fork 与上游相反的约定（防「照抄上游 SQL」再踩）
    // ============================================================

    @Test
    @DisplayName("getRouterPath:parent_id=0 + menu_type='M' + is_frame='1' 补 '/' 前缀(NO_FRAME=\"1\")")
    void topLevelDirGetsSlashPrefixBecauseNoFrameIsOne()
    {
        // 本 fork UserConstants.NO_FRAME = "1"(:46) —— 与上游 RuoYi 的 "0" 相反。
        // 按上游直觉写 is_frame='0' 会让顶层路由拿不到前导 '/',退化成相对路径。
        SysMenu opc = dir(2000L, "OPC业务", 0L, "opc", "OpcBiz", "star");
        assertEquals("1", opc.getIsFrame());
        assertEquals("/opc", service.getRouterPath(opc));

        SysMenu wrong = dir(2099L, "错误示例", 0L, "wrong", "Wrong", "star");
        wrong.setIsFrame("0");
        assertEquals("wrong", service.getRouterPath(wrong), "is_frame='0' 在本 fork 里是外链,不加前缀");

        SysMenu second = dir(2001L, "智能体市场", 2000L, "marketplace", "OpcMarketplace", "shopping");
        assertEquals("marketplace", service.getRouterPath(second), "二级目录 path 保持相对");
    }

    @Test
    @DisplayName("getComponent:二级目录是 ParentView 不是 Layout,顶层目录才是 Layout")
    void nestedDirUsesParentViewNotLayout()
    {
        SysMenu opc = dir(2000L, "OPC业务", 0L, "opc", "OpcBiz", "star");
        SysMenu second = dir(2001L, "智能体市场", 2000L, "marketplace", "OpcMarketplace", "shopping");
        SysMenu leaf = leaf(2002L, "列表", 2001L, "index", "opc/marketplace/index", "OpcMarketplaceIndex", "list");

        assertEquals("Layout", service.getComponent(opc), "parent_id=0 且 component 空 → Layout(:489)");
        assertEquals("ParentView", service.getComponent(second), "parent_id!=0 且 type='M' → ParentView(:498 isParentView)");
        assertEquals("opc/marketplace/index", service.getComponent(leaf), "叶子 component 原样取值");
    }

    @Test
    @DisplayName("route_name 为空回退 capitalize(path) —— path='index' 会撞内置 Index 路由")
    void emptyRouteNameFallsBackToCapitalizedPath()
    {
        // 迁移脚本里每行都显式给了 route_name 正是为了避开这个。
        // 这里钉住回退行为本身,好让后人知道为什么不能把 route_name 留空。
        SysMenu leaf = leaf(2002L, "智能体市场列表", 2001L, "index", "opc/marketplace/index", null, "list");
        assertFalse(StringUtils.isNotEmpty(leaf.getRouteName()));
        assertEquals("Index", service.getRouteName(leaf.getRouteName(), leaf.getPath()));

        SysMenu named = leaf(2002L, "智能体市场列表", 2001L, "index", "opc/marketplace/index", "OpcMarketplaceIndex", "list");
        assertEquals("OpcMarketplaceIndex", service.getRouteName(named));
    }

    // ============================================================
    // helpers
    // ============================================================

    private static String fullPath(RouterVo... chain)
    {
        StringBuilder sb = new StringBuilder();
        for (RouterVo r : chain)
        {
            String p = r.getPath();
            if (p.startsWith("/"))
            {
                sb.append(p);
            }
            else if (sb.length() == 0)
            {
                sb.append('/').append(p);
            }
            else
            {
                sb.append('/').append(p);
            }
        }
        return sb.toString();
    }

    private static SysMenu dir(Long id, String name, Long parentId, String path, String routeName, String icon)
    {
        return menu(id, name, parentId, path, null, "M", routeName, icon);
    }

    private static SysMenu leaf(Long id, String name, Long parentId, String path, String component, String routeName, String icon)
    {
        return menu(id, name, parentId, path, component, "C", routeName, icon);
    }

    private static SysMenu menu(Long id, String name, Long parentId, String path, String component, String type,
            String routeName, String icon)
    {
        SysMenu m = new SysMenu();
        m.setMenuId(id);
        m.setMenuName(name);
        m.setParentId(parentId);
        m.setOrderNum(1);
        m.setPath(path);
        m.setComponent(component);
        m.setMenuType(type);
        m.setRouteName(routeName);
        m.setIcon(icon);
        m.setIsFrame("1");   // 本 fork 的 NO_FRAME,与 ry_20260417.sql:164-166 的内置目录一致
        m.setIsCache("0");
        m.setVisible("0");
        m.setStatus("0");
        return m;
    }
}
