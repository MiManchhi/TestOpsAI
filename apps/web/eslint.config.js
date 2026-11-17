import vue from 'eslint-plugin-vue';
import ts from 'typescript-eslint';

export default ts.config(
  { ignores: ['dist/**'] },
  ...ts.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    rules: {
      // Prettier owns layout; keep Vue's semantic and correctness rules.
      'vue/singleline-html-element-content-newline': 'off',
      'vue/max-attributes-per-line': 'off',
    },
  },
  { files: ['**/*.vue'], languageOptions: { parserOptions: { parser: ts.parser } } },
);
