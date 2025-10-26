const { createApp, ref, onMounted, computed } = Vue;

const app = createApp({
    setup() {
        // 检查 Element Plus 是否正确加载
        console.log('ElementPlus 对象:', typeof ElementPlus);

        // 尝试不同的方式获取 ElMessage
        let ElMessage;
        if (typeof ElementPlus !== 'undefined' && ElementPlus.ElMessage) {
            ElMessage = ElementPlus.ElMessage;
        } else if (typeof window.ElMessage !== 'undefined') {
            ElMessage = window.ElMessage;
        } else {
            console.error('无法找到 ElMessage 组件');
            // 创建一个简单的替代品
            ElMessage = {
                error: (msg) => console.error('Error:', msg),
                warning: (msg) => console.warn('Warning:', msg),
                success: (msg) => console.log('Success:', msg)
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
        
        // 表单数据
        const createTaskForm = ref({
            agentId: '',
            scriptLang: '',
            scriptContent: '',
            timeoutSec: 300
        });
        
        const batchTaskForm = ref({
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
        const autoRefreshLogs = ref(false);
        const onlineAgents = ref([]);
        const agentIdFilter = ref(''); // 用于筛选特定agent的任务

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
                    onlineAgents.value = response.data.content.filter(agent => agent.status === 'ONLINE');
                } else if (Array.isArray(response.data)) {
                    onlineAgents.value = response.data.filter(agent => agent.status === 'ONLINE');
                } else {
                    onlineAgents.value = [];
                }
            } catch (error) {
                console.error('获取在线客户端失败:', error);
                onlineAgents.value = [];
            }
        };

        const submitCreateTask = async () => {
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
                
                // 使用查询参数传递 agentId
                const params = new URLSearchParams();
                params.append('agentId', createTaskForm.value.agentId);
                
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
            if (!batchTaskForm.value.selectedAgents.length || !batchTaskForm.value.scriptContent) {
                ElMessage.warning('请选择客户端并填写脚本内容');
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
                batchTaskForm.value.selectedAgents.forEach(id => params.append('agentIds', id));
                
                await api.post(`/web/tasks/batch?${params.toString()}`, taskSpec);
                ElMessage.success('批量任务创建成功');
                showBatchTaskDialog.value = false;
                // 重置表单
                batchTaskForm.value = {
                    selectedAgents: [],
                    scriptLang: '',
                    scriptContent: '',
                    timeoutSec: 300
                };
                fetchTasks(); // 刷新任务列表
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
                const response = await api.get(`/web/tasks/${selectedTask.value.taskId}/logs`);
                console.log('原始日志数据:', response.data); // 调试：查看原始数据
                taskLogs.value = response.data || [];
                console.log('设置的日志:', taskLogs.value); // 调试：查看设置后的数据
            } catch (error) {
                console.error('获取任务日志失败:', error);
                ElMessage.error('获取任务日志失败');
            }
        };

        const clearLogs = () => {
            taskLogs.value = [];
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
        
        const clearAgentFilter = () => {
            agentIdFilter.value = '';
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
            // 表单数据
            createTaskForm,
            batchTaskForm,
            // 其他状态
            createTaskLoading,
            batchTaskLoading,
            selectedTask,
            taskLogs,
            autoRefreshLogs,
            onlineAgents,
            totalTasks,
            agentIdFilter,
            // 计算属性
            filteredTasks,
            // 方法
            clearAgentFilter,
            refreshTasks: fetchTasks,
            submitCreateTask,
            submitBatchTask,
            viewTaskDetail,
            viewTaskLogs,
            refreshLogs,
            clearLogs,
            cancelTask,
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
        };
    },
});

app.use(ElementPlus);
app.mount('#app');
