const { createApp } = Vue;
const { ElMessage, ElMessageBox } = ElementPlus;

createApp({
    data() {
        return {
            isLoggedIn: false,
            userInfo: {},
            activeMenu: 'dashboard',
            loginForm: {
                username: '',
                password: ''
            },
            loginLoading: false,
            stats: {},
            agents: [],
            tasks: [],
            taskLogs: [],
            onlineAgents: [],
            showBatchTaskDialog: false,
            showLogsDialog: false,
            showScriptDialog: false,
            batchTaskForm: {
                selectedAgents: [],
                scriptLang: 'bash',
                scriptContent: '',
                timeoutSec: 300
            },
            batchTaskLoading: false,
            pollingTimer: null
        }
    },
    mounted() {
        this.checkLogin();
        this.setupAxiosInterceptors();
        this.setupAxiosDefaults();
    },
    beforeUnmount() {
        if (this.pollingTimer) {
            clearInterval(this.pollingTimer);
        }
    },
    methods: {
        checkLogin() {
            const token = localStorage.getItem(CONFIG.STORAGE_KEYS.TOKEN);
            const userInfo = localStorage.getItem(CONFIG.STORAGE_KEYS.USER_INFO);
            if (token && userInfo) {
                this.isLoggedIn = true;
                this.userInfo = JSON.parse(userInfo);
                axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
                this.loadDashboard();
                this.startPolling();
            }
        },
        
        setupAxiosDefaults() {
            axios.defaults.baseURL = CONFIG.API_BASE_URL;
            axios.defaults.timeout = CONFIG.APP_SETTINGS.REQUEST_TIMEOUT;
        },
        
        setupAxiosInterceptors() {
            axios.interceptors.response.use(
                response => response,
                error => {
                    if (error.response && error.response.status === 401) {
                        this.logout();
                        ElMessage.error('登录已过期，请重新登录');
                    } else if (error.code === 'ECONNABORTED') {
                        ElMessage.error('请求超时，请检查网络连接');
                    } else {
                        console.error('API Error:', error);
                    }
                    return Promise.reject(error);
                }
            );
        },
        
        async login() {
            if (!this.loginForm.username || !this.loginForm.password) {
                ElMessage.error('请输入用户名和密码');
                return;
            }
            
            this.loginLoading = true;
            try {
                const response = await axios.post(CONFIG.API_ENDPOINTS.LOGIN, this.loginForm);
                const { token, username, role, email } = response.data;
                
                localStorage.setItem(CONFIG.STORAGE_KEYS.TOKEN, token);
                localStorage.setItem(CONFIG.STORAGE_KEYS.USER_INFO, JSON.stringify({ username, role, email }));
                
                this.isLoggedIn = true;
                this.userInfo = { username, role, email };
                axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
                
                ElMessage.success('登录成功');
                this.loadDashboard();
                this.startPolling();
            } catch (error) {
                ElMessage.error(error.response?.data?.error || '登录失败');
            } finally {
                this.loginLoading = false;
            }
        },
        
        logout() {
            localStorage.removeItem(CONFIG.STORAGE_KEYS.TOKEN);
            localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_INFO);
            delete axios.defaults.headers.common['Authorization'];
            this.isLoggedIn = false;
            this.userInfo = {};
            this.activeMenu = 'dashboard';
            this.stopPolling();
        },
        
        startPolling() {
            this.pollingTimer = setInterval(() => {
                if (this.activeMenu === 'dashboard') {
                    this.loadDashboard();
                } else if (this.activeMenu === 'agents') {
                    this.loadAgents();
                } else if (this.activeMenu === 'tasks') {
                    this.loadTasks();
                }
            }, CONFIG.APP_SETTINGS.POLLING_INTERVAL);
        },
        
        stopPolling() {
            if (this.pollingTimer) {
                clearInterval(this.pollingTimer);
                this.pollingTimer = null;
            }
        },
        
        handleMenuSelect(key) {
            this.activeMenu = key;
            switch (key) {
                case 'dashboard':
                    this.loadDashboard();
                    break;
                case 'agents':
                    this.loadAgents();
                    break;
                case 'tasks':
                    this.loadTasks();
                    break;
                case 'scripts':
                    // 脚本管理功能待实现
                    break;
            }
        },
        
        async loadDashboard() {
            try {
                const response = await axios.get(CONFIG.API_ENDPOINTS.DASHBOARD_STATS);
                this.stats = response.data;
            } catch (error) {
                console.error('加载仪表盘数据失败:', error);
            }
        },
        
        async loadAgents() {
            try {
                const response = await axios.get(CONFIG.API_ENDPOINTS.AGENTS);
                this.agents = response.data.content || [];
                this.onlineAgents = this.agents.filter(agent => agent.status === 'ONLINE');
            } catch (error) {
                console.error('加载客户端列表失败:', error);
            }
        },
        
        async loadTasks() {
            try {
                const response = await axios.get(CONFIG.API_ENDPOINTS.TASKS);
                this.tasks = response.data.content || [];
            } catch (error) {
                console.error('加载任务列表失败:', error);
            }
        },
        
        async viewAgentTasks(agent) {
            try {
                const url = Utils.formatApiUrl(CONFIG.API_ENDPOINTS.AGENT_TASKS, { agentId: agent.agentId });
                const response = await axios.get(url);
                const tasks = response.data.content || [];
                
                let message = `客户端 ${agent.hostname} 的任务:\n`;
                if (tasks.length === 0) {
                    message += '暂无任务';
                } else {
                    tasks.forEach(task => {
                        const status = this.getTaskStatusText(task.status);
                        message += `- ${task.taskId}: ${status}\n`;
                    });
                }
                
                ElMessageBox.alert(message, '客户端任务', {
                    confirmButtonText: '确定'
                });
            } catch (error) {
                ElMessage.error('加载客户端任务失败');
            }
        },
        
        async createTaskForAgent(agent) {
            try {
                const { value } = await ElMessageBox.prompt('请输入要执行的脚本内容:', `为 ${agent.hostname} 创建任务`, {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    inputType: 'textarea'
                });
                
                const taskSpec = {
                    scriptLang: agent.osType === 'WINDOWS' ? 'powershell' : 'bash',
                    scriptContent: value,
                    timeoutSec: 300
                };
                
                await axios.post(`${CONFIG.API_ENDPOINTS.CREATE_TASK}?agentId=${agent.agentId}`, taskSpec);
                ElMessage.success('任务创建成功');
                
                if (this.activeMenu === 'tasks') {
                    this.loadTasks();
                }
            } catch (error) {
                if (error !== 'cancel') {
                    ElMessage.error('创建任务失败');
                }
            }
        },
        
        async submitBatchTask() {
            if (this.batchTaskForm.selectedAgents.length === 0) {
                ElMessage.error('请选择至少一个客户端');
                return;
            }
            
            if (!this.batchTaskForm.scriptContent) {
                ElMessage.error('请输入脚本内容');
                return;
            }
            
            this.batchTaskLoading = true;
            try {
                const taskSpec = {
                    scriptLang: this.batchTaskForm.scriptLang,
                    scriptContent: this.batchTaskForm.scriptContent,
                    timeoutSec: this.batchTaskForm.timeoutSec
                };
                
                const agentIds = this.batchTaskForm.selectedAgents.join(',');
                await axios.post(`${CONFIG.API_ENDPOINTS.BATCH_TASKS}?agentIds=${agentIds}`, taskSpec);
                
                ElMessage.success(`成功为 ${this.batchTaskForm.selectedAgents.length} 个客户端创建任务`);
                this.showBatchTaskDialog = false;
                this.resetBatchTaskForm();
                
                if (this.activeMenu === 'tasks') {
                    this.loadTasks();
                }
            } catch (error) {
                ElMessage.error('批量创建任务失败');
            } finally {
                this.batchTaskLoading = false;
            }
        },
        
        async viewTaskLogs(task) {
            try {
                const url = Utils.formatApiUrl(CONFIG.API_ENDPOINTS.TASK_LOGS, { taskId: task.taskId });
                const response = await axios.get(url);
                this.taskLogs = response.data || [];
                this.showLogsDialog = true;
            } catch (error) {
                ElMessage.error('加载任务日志失败');
            }
        },
        
        resetBatchTaskForm() {
            this.batchTaskForm = {
                selectedAgents: [],
                scriptLang: 'bash',
                scriptContent: '',
                timeoutSec: 300
            };
        },
        
        getTaskStatusType(status) {
            const statusConfig = CONFIG.TASK_STATUS[status];
            return statusConfig ? statusConfig.type : 'info';
        },
        
        getTaskStatusText(status) {
            const statusConfig = CONFIG.TASK_STATUS[status];
            return statusConfig ? statusConfig.text : status;
        },
        
        formatDateTime(dateTime) {
            return Utils.formatDateTime(dateTime);
        }
    }
}).use(ElementPlus).mount('#app');
