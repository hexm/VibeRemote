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
            batchTaskLoading: false
        }
    },
    mounted() {
        this.checkLogin();
        this.setupAxiosInterceptors();
    },
    methods: {
        checkLogin() {
            const token = localStorage.getItem('token');
            const userInfo = localStorage.getItem('userInfo');
            if (token && userInfo) {
                this.isLoggedIn = true;
                this.userInfo = JSON.parse(userInfo);
                axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
                this.loadDashboard();
            }
        },
        
        setupAxiosInterceptors() {
            axios.interceptors.response.use(
                response => response,
                error => {
                    if (error.response && error.response.status === 401) {
                        this.logout();
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
                const response = await axios.post('/api/auth/login', this.loginForm);
                const { token, username, role, email } = response.data;
                
                localStorage.setItem('token', token);
                localStorage.setItem('userInfo', JSON.stringify({ username, role, email }));
                
                this.isLoggedIn = true;
                this.userInfo = { username, role, email };
                axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
                
                ElMessage.success('登录成功');
                this.loadDashboard();
            } catch (error) {
                ElMessage.error(error.response?.data?.error || '登录失败');
            } finally {
                this.loginLoading = false;
            }
        },
        
        logout() {
            localStorage.removeItem('token');
            localStorage.removeItem('userInfo');
            delete axios.defaults.headers.common['Authorization'];
            this.isLoggedIn = false;
            this.userInfo = {};
            this.activeMenu = 'dashboard';
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
                const response = await axios.get('/api/web/dashboard/stats');
                this.stats = response.data;
            } catch (error) {
                ElMessage.error('加载仪表盘数据失败');
            }
        },
        
        async loadAgents() {
            try {
                const response = await axios.get('/api/web/agents');
                this.agents = response.data.content || [];
                this.onlineAgents = this.agents.filter(agent => agent.status === 'ONLINE');
            } catch (error) {
                ElMessage.error('加载客户端列表失败');
            }
        },
        
        async loadTasks() {
            try {
                const response = await axios.get('/api/web/tasks');
                this.tasks = response.data.content || [];
            } catch (error) {
                ElMessage.error('加载任务列表失败');
            }
        },
        
        async viewAgentTasks(agent) {
            try {
                const response = await axios.get(`/api/web/agents/${agent.agentId}/tasks`);
                const tasks = response.data.content || [];
                
                let message = `客户端 ${agent.hostname} 的任务:\n`;
                if (tasks.length === 0) {
                    message += '暂无任务';
                } else {
                    tasks.forEach(task => {
                        message += `- ${task.taskId}: ${this.getTaskStatusText(task.status)}\n`;
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
                
                await axios.post(`/api/web/tasks/create?agentId=${agent.agentId}`, taskSpec);
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
                await axios.post(`/api/web/tasks/batch?agentIds=${agentIds}`, taskSpec);
                
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
                const response = await axios.get(`/api/web/tasks/${task.taskId}/logs`);
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
            const statusMap = {
                'PENDING': 'info',
                'RUNNING': 'warning',
                'SUCCESS': 'success',
                'FAILED': 'danger',
                'TIMEOUT': 'danger',
                'CANCELLED': 'info'
            };
            return statusMap[status] || 'info';
        },
        
        getTaskStatusText(status) {
            const statusMap = {
                'PENDING': '等待中',
                'RUNNING': '运行中',
                'SUCCESS': '成功',
                'FAILED': '失败',
                'TIMEOUT': '超时',
                'CANCELLED': '已取消'
            };
            return statusMap[status] || status;
        }
    }
}).use(ElementPlus).mount('#app');
