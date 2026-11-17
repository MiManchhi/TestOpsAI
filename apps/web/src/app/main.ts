import { createApp } from 'vue';
import App from './App.vue';
import './style.css';
import { createAppRouter } from './router';

createApp(App).use(createAppRouter()).mount('#app');
