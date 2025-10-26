// 这是tasks.js需要添加的完整代码片段
// 请将以下代码整合到现有的tasks.js中

// ========== 需要添加的ref变量 ==========
// 在setup()函数开头添加：

const activeTab = ref('normal'); // Tab状态

// 批量任务相关状态
const batchTasks = ref([]);
const batchLoading = ref(false);
const batchSearchKeyword = ref('');
const batchCurrentPage = ref(1);
const batchPageSize = ref(20);
const totalBatchTasks = ref(0);

// 批量任务详情
const showBatchDetailDialog = ref(false);
const selectedBatchTask = ref(null);
const batchTaskTasks = ref([]);
const batchTaskTasksLoading = ref(false);

// 修改创建任务表单，添加taskName字段
// 将原来的 createTaskForm 修改为：
const createTaskForm = ref({
    taskName: '', // 新增
    agentId: '',
    scriptLang: '',
    scriptContent: '',
    timeoutSec: 300
});

// 修改批量任务表单，添加batchName字段
// 将原来的 batchTaskForm 修改为：
const batchTaskForm = ref({
    batchName: '', // 新增
    selectedAgents: [],
    scriptLang: '',
    scriptContent: '',
    timeoutSec: 300
});

// ========== 需要添加的方法 ==========

// Tab切换处理
const handleTabClick = (tab) => {
    if (activeTab.value === 'batch' && batchTasks.value.length === 0) {
        fetchBatchTasks();
    }
};

// 获取批量任务列表
const fetchBatchTasks = async () => {
    batchLoading.value = true;
    try {
        const response = await api.get('/web/batch-tasks', {
            params: {
                page: batchCurrentPage.value - 1,
                size: batchPageSize.value
            }
        });
        batchTasks.value = response.data.content;
        totalBatchTasks.value = response.data.totalElements;
    } catch (error) {
        console.error('获取批量任务列表失败:', error);
        ElMessage.error('获取批量任务列表失败: ' + (error.response?.data?.message || error.message));
    } finally {
        batchLoading.value = false;
    }
};

// 刷新批量任务列表
const refreshBatchTasks = () => {
    fetchBatchTasks();
};

// 批量任务分页处理
const handleBatchSizeChange = (val) => {
    batchPageSize.value = val;
    batchCurrentPage.value = 1;
    fetchBatchTasks();
};

const handleBatchCurrentChange = (val) => {
    batchCurrentPage.value = val;
    fetchBatchTasks();
};

// 查看批量任务详情
const viewBatchTaskDetail = async (batchTask) => {
    selectedBatchTask.value = batchTask;
    showBatchDetailDialog.value = true;
    
    // 加载子任务列表
    batchTaskTasksLoading.value = true;
    try {
        const response = await api.get(`/web/batch-tasks/${batchTask.batchId}/tasks`);
        batchTaskTasks.value = response.data;
    } catch (error) {
        console.error('获取子任务失败:', error);
        ElMessage.error('获取子任务失败: ' + (error.response?.data?.message || error.message));
    } finally {
        batchTaskTasksLoading.value = false;
    }
};

// 取消批量任务
const cancelBatchTask = async (batchTask) => {
    try {
        await ElMessageBox.confirm(
            `确定要取消批量任务"${batchTask.batchName}"吗？这将取消所有未完成的子任务。`,
            '确认取消',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            }
        );
        
        await api.post(`/web/batch-tasks/${batchTask.batchId}/cancel`);
        ElMessage.success('批量任务已取消');
        fetchBatchTasks();
        
        // 如果详情对话框打开，刷新详情
        if (showBatchDetailDialog.value && selectedBatchTask.value?.batchId === batchTask.batchId) {
            viewBatchTaskDetail(batchTask);
        }
    } catch (error) {
        if (error !== 'cancel') {
            console.error('取消批量任务失败:', error);
            ElMessage.error('取消失败: ' + (error.response?.data?.message || error.message));
        }
    }
};

// 批量任务状态文本
const getBatchStatusText = (status) => {
    const map = {
        'PENDING': '等待中',
        'RUNNING': '运行中',
        'COMPLETED': '已完成',
        'PARTIAL_FAILED': '部分失败',
        'FAILED': '失败'
    };
    return map[status] || status;
};

// 批量任务状态类型（用于el-tag的type）
const getBatchStatusType = (status) => {
    const map = {
        'PENDING': 'info',
        'RUNNING': 'primary',
        'COMPLETED': 'success',
        'PARTIAL_FAILED': 'warning',
        'FAILED': 'danger'
    };
    return map[status] || 'info';
};

// 获取Agent名称（辅助函数）
const getAgentName = (agentId) => {
    const agent = onlineAgents.value.find(a => a.agentId === agentId);
    return agent ? agent.hostname : agentId.substring(0, 8) + '...';
};

// ========== 需要修改的现有方法 ==========

// 修改 submitCreateTask 方法，支持任务名称
// 找到原来的submitCreateTask方法，在创建任务前添加：
/*
const submitCreateTask = async () => {
    if (!createTaskForm.value.agentId || !createTaskForm.value.scriptContent || !createTaskForm.value.scriptLang) {
        ElMessage.warning('请填写完整的任务信息');
        return;
    }
    
    // 新增：如果没有输入任务名称，生成默认名称
    if (!createTaskForm.value.taskName) {
        const agentName = getAgentName(createTaskForm.value.agentId);
        const timestamp = new Date().toLocaleString('zh-CN', { 
            month: '2-digit', 
            day: '2-digit', 
            hour: '2-digit', 
            minute: '2-digit' 
        }).replace(/\//g, '-');
        createTaskForm.value.taskName = `任务-${agentName}-${timestamp}`;
    }
    
    createTaskLoading.value = true;
    try {
        const taskSpec = {
            taskName: createTaskForm.value.taskName, // 新增
            scriptLang: createTaskForm.value.scriptLang,
            scriptContent: createTaskForm.value.scriptContent,
            timeoutSec: createTaskForm.value.timeoutSec
        };
        
        // ... 原有代码
    }
}
*/

// 修改 submitBatchTask 方法，调用新的批量任务API
// 找到原来的submitBatchTask方法，完全替换为：
/*
const submitBatchTask = async () => {
    if (!batchTaskForm.value.selectedAgents.length || !batchTaskForm.value.scriptContent) {
        ElMessage.warning('请选择客户端并填写脚本内容');
        return;
    }
    
    // 如果没有输入批量任务名称，生成默认名称
    if (!batchTaskForm.value.batchName) {
        const timestamp = new Date().toLocaleString('zh-CN', { 
            month: '2-digit', 
            day: '2-digit', 
            hour: '2-digit', 
            minute: '2-digit' 
        }).replace(/\//g, '-');
        batchTaskForm.value.batchName = `批量任务-${timestamp}`;
    }
    
    batchTaskLoading.value = true;
    try {
        // 使用新的批量任务API
        const params = new URLSearchParams();
        params.append('batchName', batchTaskForm.value.batchName);
        batchTaskForm.value.selectedAgents.forEach(id => params.append('agentIds', id));
        
        const taskSpec = {
            scriptLang: batchTaskForm.value.scriptLang,
            scriptContent: batchTaskForm.value.scriptContent,
            timeoutSec: batchTaskForm.value.timeoutSec
        };
        
        const response = await api.post(`/web/batch-tasks/create?${params.toString()}`, taskSpec);
        console.log('批量任务创建成功:', response);
        ElMessage.success(`批量任务创建成功！已为${response.data.targetAgentCount}个客户端创建任务`);
        
        showBatchTaskDialog.value = false;
        
        // 重置表单
        batchTaskForm.value = {
            batchName: '',
            selectedAgents: [],
            scriptLang: '',
            scriptContent: '',
            timeoutSec: 300
        };
        
        // 切换到批量任务Tab并刷新
        activeTab.value = 'batch';
        fetchBatchTasks();
        
    } catch (error) {
        console.error('创建批量任务失败:', error);
        ElMessage.error('创建批量任务失败: ' + (error.response?.data?.message || error.message));
    } finally {
        batchTaskLoading.value = false;
    }
};
*/

// ========== 需要在return中添加的内容 ==========
/*
在return语句中添加以下内容（保留所有原有的return内容）：

return {
    // ... 保留所有原有的return内容 ...
    
    // 新增：Tab相关
    activeTab,
    handleTabClick,
    
    // 新增：批量任务列表相关
    batchTasks,
    batchLoading,
    batchSearchKeyword,
    batchCurrentPage,
    batchPageSize,
    totalBatchTasks,
    fetchBatchTasks,
    refreshBatchTasks,
    handleBatchSizeChange,
    handleBatchCurrentChange,
    
    // 新增：批量任务详情相关
    showBatchDetailDialog,
    selectedBatchTask,
    batchTaskTasks,
    batchTaskTasksLoading,
    viewBatchTaskDetail,
    cancelBatchTask,
    
    // 新增：辅助函数
    getBatchStatusText,
    getBatchStatusType,
    getAgentName
};
*/
