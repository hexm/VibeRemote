const { createApp, ref, onMounted, computed } = Vue;

const app = createApp({
    setup() {
        const userInfo = ref({});
        const scripts = ref([]);
        const loading = ref(false);
        const searchKeyword = ref('');
        const categoryFilter = ref('');
        const langFilter = ref('');

        const checkLogin = () => {
            const token = localStorage.getItem('jwt_token');
            if (!token) {
                window.location.href = '/login.html';
                return;
            }
            try {
                const payload = JSON.parse(atob(token.split('.')[1]));
                userInfo.value = { username: payload.sub };
            } catch (e) {
                localStorage.removeItem('jwt_token');
                window.location.href = '/login.html';
            }
        };

        const logout = () => {
            localStorage.removeItem('jwt_token');
            window.location.href = '/login.html';
        };

        const fetchScripts = async () => {
            loading.value = true;
            try {
                const response = await api.get('/scripts');
                scripts.value = response.data;
            } catch (error) {
                ElMessage.error('获取脚本列表失败');
            } finally {
                loading.value = false;
            }
        };

        const filteredScripts = computed(() => {
            // Filtering logic would go here
            return scripts.value;
        });

        onMounted(() => {
            checkLogin();
            fetchScripts();
        });

        return {
            userInfo,
            scripts,
            loading,
            searchKeyword,
            categoryFilter,
            langFilter,
            filteredScripts,
            logout,
            refreshScripts: fetchScripts,
            formatDateTime,
            getScriptLangType: (lang) => {
                const map = { bash: 'success', powershell: 'primary', cmd: 'info' };
                return map[lang] || 'info';
            },
            getCategoryName: (cat) => cat || '其他',
            // Placeholders
            selectScript: () => {},
            handleScriptAction: () => {},
            executeScript: () => {},
            editScript: () => {},
        };
    },
});

app.use(ElementPlus);
app.mount('#app');
