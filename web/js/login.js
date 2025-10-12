const { createApp, ref } = Vue;

const app = createApp({
    setup() {
        const loginForm = ref({
            username: '',
            password: '',
        });
        const loginFormRef = ref(null);
        const loginLoading = ref(false);
        const rememberMe = ref(false);

        const loginRules = {
            username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
            password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
        };

        const handleLogin = () => {
            loginFormRef.value.validate(async (valid) => {
                if (valid) {
                    loginLoading.value = true;
                    try {
                        const response = await api.post('/auth/login', {
                            username: loginForm.value.username,
                            password: loginForm.value.password,
                        });
                        const token = response.data.token;
                        localStorage.setItem('jwt_token', token);
                        window.location.href = '/index.html';
                    } catch (error) {
                        ElMessage.error(error.response?.data?.message || '登录失败');
                    } finally {
                        loginLoading.value = false;
                    }
                }
            });
        };

        return {
            loginForm,
            loginFormRef,
            loginRules,
            loginLoading,
            rememberMe,
            handleLogin,
        };
    },
});

app.use(ElementPlus);
app.mount('#app');
