const { createApp, ref, onMounted, computed } = Vue;

// 检查 Element Plus 是否正确加载
console.log('ElementPlus 对象:', typeof ElementPlus);
console.log('ElementPlus.ElMessage:', typeof ElementPlus?.ElMessage);

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

const app = createApp({
    setup() {
        const agents = ref([]);
        const loading = ref(false);
        const searchKeyword = ref('');
        const statusFilter = ref('');
        const currentPage = ref(1);
        const pageSize = ref(10);
        const selectedAgents = ref([]);
        const showBatchTaskDialog = ref(false);
        const showAgentDetailDialog = ref(false);
        const selectedAgent = ref(null);
        const batchTaskLoading = ref(false);
        const batchTaskForm = ref({
            scriptLang: '',
            scriptContent: '',
            timeoutSec: 60
        });

        const fetchAgents = async () => {
            loading.value = true;
            try {
                console.log('正在获取客户端列表...');
                const response = await api.get('/web/agents');
                console.log('API 响应:', response);
                console.log('响应数据:', response.data);
                
                // 处理分页数据格式
                if (response.data && response.data.content) {
                    // Spring Boot 分页格式
                    agents.value = Array.isArray(response.data.content) ? response.data.content : [];
                } else if (Array.isArray(response.data)) {
                    // 直接数组格式
                    agents.value = response.data;
                } else {
                    agents.value = [];
                }
                console.log('设置的 agents 数据:', agents.value);
                
                if (agents.value.length === 0) {
                    console.log('没有找到客户端数据');
                }
            } catch (error) {
                console.error('获取客户端列表失败:', error);
                console.error('错误详情:', error.response);
                ElMessage.error('获取客户端列表失败: ' + (error.response?.data?.message || error.message));
                agents.value = []; // 确保出错时也是空数组
            } finally {
                loading.value = false;
            }
        };

        const filteredAgents = computed(() => {
            if (!agents.value || !Array.isArray(agents.value)) {
                return [];
            }
            return agents.value.filter(agent => {
                if (!agent) return false;
                const hostname = agent.hostname || '';
                const ip = agent.ip || '';
                const matchesKeyword = hostname.toLowerCase().includes(searchKeyword.value.toLowerCase()) || ip.includes(searchKeyword.value);
                const matchesStatus = !statusFilter.value || agent.status === statusFilter.value;
                return matchesKeyword && matchesStatus;
            });
        });

        onMounted(() => {
            fetchAgents();
        });

        const handleSelectionChange = (selection) => {
            selectedAgents.value = selection;
        };

        const viewAgentDetail = (agent) => {
            selectedAgent.value = agent;
            showAgentDetailDialog.value = true;
        };

        const createTaskForAgent = (agent) => {
            // 跳转到任务创建页面，传递agent信息
            window.location.href = `tasks.html?agentId=${agent.agentId}`;
        };

        const viewAgentTasks = (agent) => {
            // 跳转到任务列表页面，筛选该agent的任务
            window.location.href = `tasks.html?filter=agent&agentId=${agent.agentId}`;
        };

        const removeSelectedAgent = (agent) => {
            const index = selectedAgents.value.findIndex(a => a.agentId === agent.agentId);
            if (index > -1) {
                selectedAgents.value.splice(index, 1);
            }
        };

        const submitBatchTask = async () => {
            if (!batchTaskForm.value.scriptLang || !batchTaskForm.value.scriptContent) {
                ElMessage.warning('请填写完整的任务信息');
                return;
            }
            
            batchTaskLoading.value = true;
            try {
                const agentIds = selectedAgents.value.map(agent => agent.agentId);
                const taskSpec = {
                    scriptLang: batchTaskForm.value.scriptLang,
                    scriptContent: batchTaskForm.value.scriptContent,
                    timeoutSec: batchTaskForm.value.timeoutSec
                };
                
                // 使用查询参数传递 agentIds
                const params = new URLSearchParams();
                agentIds.forEach(id => params.append('agentIds', id));
                
                await api.post(`/web/tasks/batch?${params.toString()}`, taskSpec);
                ElMessage.success('批量任务下发成功');
                showBatchTaskDialog.value = false;
                // 重置表单
                batchTaskForm.value = {
                    scriptLang: '',
                    scriptContent: '',
                    timeoutSec: 60
                };
                selectedAgents.value = [];
            } catch (error) {
                console.error('批量任务下发失败:', error);
                ElMessage.error('批量任务下发失败');
            } finally {
                batchTaskLoading.value = false;
            }
        };

        const handleSearch = () => {
            // 搜索逻辑已在 computed 中实现
        };

        return {
            agents,
            loading,
            searchKeyword,
            statusFilter,
            currentPage,
            pageSize,
            selectedAgents,
            showBatchTaskDialog,
            showAgentDetailDialog,
            selectedAgent,
            batchTaskLoading,
            batchTaskForm,
            filteredAgents,
            totalAgents: computed(() => filteredAgents.value ? filteredAgents.value.length : 0),
            hasSelectedAgents: computed(() => selectedAgents.value && selectedAgents.value.length > 0),
            refreshAgents: fetchAgents,
            formatDateTime,
            handleSelectionChange,
            viewAgentDetail,
            createTaskForAgent,
            viewAgentTasks,
            removeSelectedAgent,
            submitBatchTask,
            handleSearch,
            handleSizeChange: (val) => { pageSize.value = val; },
            handleCurrentChange: (val) => { currentPage.value = val; },
        };
    },
});

app.use(ElementPlus);
app.mount('#app');
