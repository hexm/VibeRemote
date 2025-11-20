const { createApp, ref, onMounted, computed, watch } = Vue;

const app = createApp({
    setup() {
        // 检查 Element Plus 是否正确加载
        console.log('ElementPlus 对象:', typeof ElementPlus);

        // 尝试不同的方式获取 ElMessage 和 ElMessageBox
        let ElMessage, ElMessageBox;
        
        if (typeof ElementPlus !== 'undefined' && ElementPlus.ElMessage) {
            ElMessage = ElementPlus.ElMessage;
            ElMessageBox = ElementPlus.ElMessageBox;
        } else if (typeof window.ElMessage !== 'undefined') {
            ElMessage = window.ElMessage;
            ElMessageBox = window.ElMessageBox;
        } else {
            console.error('无法找到 ElMessage 组件');
            // 创建一个简单的替代品
            ElMessage = {
                error: (msg) => console.error('Error:', msg),
                warning: (msg) => console.warn('Warning:', msg),
                success: (msg) => console.log('Success:', msg)
            };
            ElMessageBox = {
                confirm: (msg, title, options) => {
                    return Promise.resolve(window.confirm(msg));
                }
            };
        }
        const tasks = ref([]);
        const loading = ref(false);
        const searchKeyword = ref('');
        const statusFilter = ref('');
        const dateRange = ref([]);
        const currentPage = ref(1);
        const pageSize = ref(10);
        const totalTasks = ref(0);
        
        // 对话框状态
        const showCreateTaskDialog = ref(false);
        const showBatchTaskDialog = ref(false);
        const showTaskDetailDialog = ref(false);
        const showLogsDialog = ref(false);
        const showHistoryDialog = ref(false);
        const showExecutionLogsDialog = ref(false);
        
        // 表单数据
        const createTaskForm = ref({
            taskName: '',
            agentId: '',
            scriptLang: '',
            scriptContent: '',
            timeoutSec: 300
        });
        
        const batchTaskForm = ref({
            batchName: '',
            selectedAgents: [],
            scriptLang: '',
            scriptContent: '',
            timeoutSec: 300
        });
        
        // 其他状态
        const createTaskLoading = ref(false);
        const batchTaskLoading = ref(false);
        const selectedTask = ref(null);
        const taskLogs = ref([]);
        const taskLogContent = ref(''); // 日志内容（字符串）
        const taskLogTotalLines = ref(0); // 总行数
        const taskLogHasMore = ref(false); // 是否还有更多
        const taskLogStatus = ref(''); // 任务状态
        const autoRefreshLogs = ref(false);
        const onlineAgents = ref([]);
        const agentIdFilter = ref(''); // 用于筛选特定agent的任务
        
        // 历史相关状态
        const taskExecutions = ref([]); // 任务执行历史列表
        const historyLoading = ref(false); // 历史加载状态
        const selectedExecution = ref(null); // 选中的执行记录
        const executionLogContent = ref(''); // 历史执行日志内容
        const executionLogTotalLines = ref(0); // 历史日志总行数
        const executionLogLoading = ref(false); // 历史日志加载状态

        // Tab状态
        const activeTab = ref('normal');

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

        const fetchTasks = async () => {
            loading.value = true;
            try {
                console.log('正在获取任务列表...');
                // 传递分页参数
                const response = await api.get('/web/tasks', {
                    params: {
                        page: currentPage.value - 1, // Spring Boot 分页从0开始
                        size: pageSize.value
                    }
                });
                console.log('API 响应:', response);
                console.log('响应数据:', response.data);
                
                // 处理分页数据格式
                if (response.data && response.data.content) {
                    // Spring Boot 分页格式
                    tasks.value = Array.isArray(response.data.content) ? response.data.content : [];
                    totalTasks.value = response.data.totalElements || 0;
                } else if (Array.isArray(response.data)) {
                    // 直接数组格式
                    tasks.value = response.data;
                    totalTasks.value = response.data.length;
                } else {
                    tasks.value = [];
                    totalTasks.value = 0;
                }
                console.log('设置的 tasks 数据:', tasks.value);
                
                if (tasks.value.length === 0) {
                    console.log('没有找到任务数据');
                }
            } catch (error) {
                console.error('获取任务列表失败:', error);
                console.error('错误详情:', error.response);
                ElMessage.error('获取任务列表失败: ' + (error.response?.data?.message || error.message));
                tasks.value = []; // 确保出错时也是空数组
                totalTasks.value = 0;
            } finally {
                loading.value = false;
            }
        };

        const fetchOnlineAgents = async () => {
            try {
                const response = await api.get('/web/agents');
                if (response.data && response.data.content) {
                    // 获取所有客户端（包括在线和离线），用于筛选下拉框
                    onlineAgents.value = response.data.content;
                } else if (Array.isArray(response.data)) {
                    onlineAgents.value = response.data;
                } else {
                    onlineAgents.value = [];
                }
            } catch (error) {
                console.error('获取客户端列表失败:', error);
                onlineAgents.value = [];
            }
        };

        const submitCreateTask = async () => {
            if (!createTaskForm.value.taskName || !createTaskForm.value.taskName.trim()) {
                ElMessage.warning('请输入任务名称');
                return;
            }
            if (!createTaskForm.value.agentId || !createTaskForm.value.scriptContent || !createTaskForm.value.scriptLang) {
                ElMessage.warning('请填写完整的任务信息');
                return;
            }
            
            createTaskLoading.value = true;
            try {
                const taskSpec = {
                    scriptLang: createTaskForm.value.scriptLang,
                    scriptContent: createTaskForm.value.scriptContent,
                    timeoutSec: createTaskForm.value.timeoutSec
                };
                
                // 使用查询参数传递 agentId 和 taskName
                const params = new URLSearchParams();
                params.append('agentId', createTaskForm.value.agentId);
                params.append('taskName', createTaskForm.value.taskName.trim());
                
                console.log('创建任务请求参数:', {
                    url: `/web/tasks/create?${params.toString()}`,
                    taskSpec: taskSpec,
                    agentId: createTaskForm.value.agentId
                });
                
                const response = await api.post(`/web/tasks/create?${params.toString()}`, taskSpec);
                console.log('创建任务响应:', response);
                ElMessage.success('任务创建成功');
                showCreateTaskDialog.value = false;
                // 重置表单
                createTaskForm.value = {
                    taskName: '',
                    agentId: '',
                    scriptLang: '',
                    scriptContent: '',
                    timeoutSec: 300
                };
                fetchTasks(); // 刷新任务列表
            } catch (error) {
                console.error('创建任务失败:', error);
                console.error('错误详情:', error.response);
                ElMessage.error('创建任务失败: ' + (error.response?.data?.message || error.message));
            } finally {
                createTaskLoading.value = false;
            }
        };

        const submitBatchTask = async () => {
            if (!batchTaskForm.value.batchName || !batchTaskForm.value.batchName.trim()) {
                ElMessage.warning('请输入批量任务名称');
                return;
            }
            if (!batchTaskForm.value.selectedAgents.length || !batchTaskForm.value.scriptContent || !batchTaskForm.value.scriptLang) {
                ElMessage.warning('请选择客户端并填写完整的任务信息');
                return;
            }
            
            batchTaskLoading.value = true;
            try {
                const taskSpec = {
                    scriptLang: batchTaskForm.value.scriptLang,
                    scriptContent: batchTaskForm.value.scriptContent,
                    timeoutSec: batchTaskForm.value.timeoutSec
                };
                
                const params = new URLSearchParams();
                params.append('batchName', batchTaskForm.value.batchName.trim());
                batchTaskForm.value.selectedAgents.forEach(id => params.append('agentIds', id));
                
                await api.post(`/web/batch-tasks/create?${params.toString()}`, taskSpec);
                ElMessage.success('批量任务创建成功');
                showBatchTaskDialog.value = false;
                // 重置表单
                batchTaskForm.value = {
                    batchName: '',
                    selectedAgents: [],
                    scriptLang: '',
                    scriptContent: '',
                    timeoutSec: 300
                };
                // 刷新批量任务列表
                if (activeTab.value === 'batch') {
                    fetchBatchTasks();
                }
                fetchTasks(); // 也刷新普通任务列表
            } catch (error) {
                console.error('创建批量任务失败:', error);
                ElMessage.error('创建批量任务失败: ' + (error.response?.data?.message || error.message));
            } finally {
                batchTaskLoading.value = false;
            }
        };

        const viewTaskDetail = (task) => {
            selectedTask.value = task;
            showTaskDetailDialog.value = true;
        };

        const viewTaskLogs = async (task) => {
            selectedTask.value = task;
            showLogsDialog.value = true;
            await refreshLogs();
        };

        const refreshLogs = async () => {
            if (!selectedTask.value) return;
            
            try {
                const response = await api.get(`/web/tasks/${selectedTask.value.taskId}/logs`, {
                    params: {
                        offset: 0,
                        limit: 5000
                    }
                });
                // 新的响应格式
                taskLogContent.value = response.data.content || '';
                taskLogTotalLines.value = response.data.totalLines || 0;
                taskLogHasMore.value = response.data.hasMore || false;
                taskLogStatus.value = response.data.status;
                
            } catch (error) {
                console.error('获取任务日志失败:', error);
                ElMessage.error('获取任务日志失败');
            }
        };

        const clearLogs = () => {
            taskLogContent.value = '';
            taskLogTotalLines.value = 0;
            taskLogHasMore.value = false;
        };

        const downloadTaskLog = (task) => {
            const url = `${api.defaults.baseURL}/web/tasks/${task.taskId}/logs/download`;
            window.open(url, '_blank');
        };

        const cancelTask = async (task) => {
            try {
                await api.post(`/web/tasks/${task.taskId}/cancel`);
                ElMessage.success('任务已取消');
                fetchTasks(); // 刷新任务列表
            } catch (error) {
                console.error('取消任务失败:', error);
                ElMessage.error('取消任务失败');
            }
        };

        // 重启任务
        const restartTask = async (task) => {
            try {
                await ElMessageBox.confirm(
                    `确认重启任务"${task.taskName || task.taskId}"吗？`,
                    '重启确认',
                    {
                        confirmButtonText: '确认',
                        cancelButtonText: '取消',
                        type: 'warning'
                    }
                );
                
                const response = await api.post(`/web/tasks/${task.taskId}/restart`);
                ElMessage.success(`任务已重启，当前为第${response.data.executionCount}次执行`);
                fetchTasks(); // 刷新任务列表
            } catch (error) {
                if (error !== 'cancel') {
                    console.error('重启任务失败:', error);
                    ElMessage.error('重启任务失败: ' + (error.response?.data?.message || error.message));
                }
            }
        };

        // 查看任务历史
        const viewTaskHistory = async (task) => {
            selectedTask.value = task;
            showHistoryDialog.value = true;
            historyLoading.value = true;
            
            try {
                const response = await api.get(`/web/tasks/${task.taskId}/executions`);
                taskExecutions.value = response.data;
            } catch (error) {
                console.error('获取执行历史失败:', error);
                ElMessage.error('获取执行历史失败');
            } finally {
                historyLoading.value = false;
            }
        };

        // 查看历史执行日志
        const viewExecutionLogs = async (execution) => {
            selectedExecution.value = execution;
            showExecutionLogsDialog.value = true;
            executionLogLoading.value = true;
            
            try {
                const response = await api.get(`/web/tasks/executions/${execution.id}/logs`, {
                    params: {
                        offset: 0,
                        limit: 5000
                    }
                });
                executionLogContent.value = response.data.content;
                executionLogTotalLines.value = response.data.totalLines;
            } catch (error) {
                console.error('加载历史日志失败:', error);
                ElMessage.error('加载历史日志失败');
            } finally {
                executionLogLoading.value = false;
            }
        };

        // 下载历史日志
        const downloadExecutionLog = (execution) => {
            const url = `${api.defaults.baseURL}/web/tasks/executions/${execution.id}/download`;
            window.open(url, '_blank');
        };

        // 格式化时长
        const formatDuration = (ms) => {
            if (!ms) return '-';
            const seconds = Math.floor(ms / 1000);
            const minutes = Math.floor(seconds / 60);
            const hours = Math.floor(minutes / 60);
            
            if (hours > 0) {
                return `${hours}h ${minutes % 60}m ${seconds % 60}s`;
            } else if (minutes > 0) {
                return `${minutes}m ${seconds % 60}s`;
            } else {
                return `${seconds}s`;
            }
        };

        // 格式化文件大小
        const formatFileSize = (bytes) => {
            if (!bytes) return '-';
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
            return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
        };

        const formatLogContent = (log) => {
            const timestamp = new Date(log.timestamp).toLocaleString();
            return `[${timestamp}] [${log.stream}] ${log.content}`;
        };

        const handleSearch = () => {
            // 搜索逻辑已在 computed 中实现
        };

        const filteredTasks = computed(() => {
            // 现在服务器端已经做了分页，前端只做简单的客户端过滤
            let filtered = tasks.value;
            
            // 按agentId筛选（查看特定agent的任务）
            if (agentIdFilter.value) {
                filtered = filtered.filter(task => task.agentId === agentIdFilter.value);
            }
            
            // 关键词搜索（前端过滤）
            if (searchKeyword.value) {
                filtered = filtered.filter(task => 
                    task.taskId.includes(searchKeyword.value) ||
                    task.agentId.includes(searchKeyword.value) ||
                    (task.scriptContent && task.scriptContent.includes(searchKeyword.value))
                );
            }
            
            // 状态筛选（前端过滤）
            if (statusFilter.value) {
                filtered = filtered.filter(task => task.status === statusFilter.value);
            }
            
            return filtered;
        });

        // 生成默认任务名称
        const generateDefaultTaskName = () => {
            const now = new Date();
            const dateStr = now.toISOString().slice(0, 19).replace('T', ' ');
            return `任务_${dateStr}`;
        };

        // 生成默认批量任务名称
        const generateDefaultBatchName = () => {
            const now = new Date();
            const dateStr = now.toISOString().slice(0, 19).replace('T', ' ');
            return `批量任务_${dateStr}`;
        };

        // 监听对话框打开，自动填充默认名称
        watch(showCreateTaskDialog, (newVal) => {
            if (newVal && (!createTaskForm.value.taskName || createTaskForm.value.taskName.trim() === '')) {
                createTaskForm.value.taskName = generateDefaultTaskName();
            }
        });

        watch(showBatchTaskDialog, (newVal) => {
            if (newVal && (!batchTaskForm.value.batchName || batchTaskForm.value.batchName.trim() === '')) {
                batchTaskForm.value.batchName = generateDefaultBatchName();
            }
        });

        const handleUrlParams = () => {
            const urlParams = new URLSearchParams(window.location.search);
            const filterType = urlParams.get('filter');
            const agentId = urlParams.get('agentId');
            
            if (agentId) {
                if (filterType === 'agent') {
                    // 查看任务模式：筛选显示该agent的所有任务
                    console.log('查看Agent任务模式，agentId:', agentId);
                    agentIdFilter.value = agentId;
                } else {
                    // 创建任务模式：打开任务创建对话框并预选客户端
                    console.log('创建任务模式，agentId:', agentId);
                    createTaskForm.value.agentId = agentId;
                    showCreateTaskDialog.value = true;
                }
                
                // 清除URL参数，避免刷新页面时重复打开对话框
                window.history.replaceState({}, document.title, window.location.pathname);
            }
        };

        // ========== 批量任务相关方法 ==========

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
                await api.post(`/web/batch-tasks/${batchTask.batchId}/cancel`);
                ElMessage.success('批量任务已取消');
                fetchBatchTasks();
                
                // 如果详情对话框打开，刷新详情
                if (showBatchDetailDialog.value && selectedBatchTask.value?.batchId === batchTask.batchId) {
                    viewBatchTaskDetail(batchTask);
                }
            } catch (error) {
                console.error('取消批量任务失败:', error);
                ElMessage.error('取消失败: ' + (error.response?.data?.message || error.message));
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

        onMounted(() => {
            fetchTasks();
            fetchOnlineAgents().then(() => {
                // 在获取客户端列表后处理URL参数
                handleUrlParams();
            });
        });

        return {
            tasks,
            loading,
            searchKeyword,
            statusFilter,
            dateRange,
            currentPage,
            pageSize,
            // 对话框状态
            showCreateTaskDialog,
            showBatchTaskDialog,
            showTaskDetailDialog,
            showLogsDialog,
            showHistoryDialog,
            showExecutionLogsDialog,
            // 表单数据
            createTaskForm,
            batchTaskForm,
            // 其他状态
            createTaskLoading,
            batchTaskLoading,
            selectedTask,
            taskLogs,
            taskLogContent,
            taskLogTotalLines,
            taskLogHasMore,
            taskLogStatus,
            autoRefreshLogs,
            onlineAgents,
            totalTasks,
            agentIdFilter,
            // 历史相关状态
            taskExecutions,
            historyLoading,
            selectedExecution,
            executionLogContent,
            executionLogTotalLines,
            executionLogLoading,
            // 计算属性
            filteredTasks,
            // 方法
            refreshTasks: fetchTasks,
            submitCreateTask,
            submitBatchTask,
            viewTaskDetail,
            viewTaskLogs,
            refreshLogs,
            clearLogs,
            downloadTaskLog,
            cancelTask,
            restartTask,
            viewTaskHistory,
            viewExecutionLogs,
            downloadExecutionLog,
            formatDuration,
            formatFileSize,
            handleSearch,
            formatDateTime,
            formatLogContent,
            getTaskStatusType: (status) => {
                const map = {
                    PENDING: 'info',
                    PULLED: 'warning',
                    RUNNING: 'primary',
                    SUCCESS: 'success',
                    FAILED: 'danger',
                    TIMEOUT: 'warning',
                };
                return map[status] || 'info';
            },
            getTaskStatusText: (status) => {
                const map = {
                    PENDING: '等待中',
                    PULLED: '已拉取',
                    RUNNING: '运行中',
                    SUCCESS: '成功',
                    FAILED: '失败',
                    TIMEOUT: '超时',
                };
                return map[status] || status;
            },
            getAgentName: (agentId) => {
                const agent = onlineAgents.value.find(a => a.agentId === agentId);
                return agent ? agent.hostname : agentId.substring(0, 8) + '...';
            },
            handleSizeChange: (val) => { 
                pageSize.value = val; 
                fetchTasks();
            },
            handleCurrentChange: (val) => { 
                currentPage.value = val; 
                fetchTasks();
            },
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
        };
    },
});

app.use(ElementPlus);
app.mount('#app');
