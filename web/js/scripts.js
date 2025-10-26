const { createApp, ref, onMounted, computed } = Vue;

const app = createApp({
    setup() {
        const scripts = ref([]);
        const loading = ref(false);
        const searchKeyword = ref('');
        const categoryFilter = ref('');
        const langFilter = ref('');

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
            fetchScripts();
        });

        return {
            scripts,
            loading,
            searchKeyword,
            categoryFilter,
            langFilter,
            filteredScripts,
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
