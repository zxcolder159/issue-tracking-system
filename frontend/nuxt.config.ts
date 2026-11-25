export default defineNuxtConfig({
  compatibilityDate: '2026-11-17',
  modules: ['@nuxt/ui'],
  ui: { fonts: false },
  colorMode: { preference: 'light' },
  css: ['~/assets/css/main.css'],
  devtools: { enabled: true },
  nitro: { preset: 'bun' },
  typescript: {
    tsConfig: {
      vueCompilerOptions: {
        plugins: ['@vue/language-plugin-pug'],
      },
    },
  },
})
