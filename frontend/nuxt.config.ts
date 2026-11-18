export default defineNuxtConfig({
  compatibilityDate: '2026-11-17',
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
