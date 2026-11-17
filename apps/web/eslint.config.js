import vue from 'eslint-plugin-vue';
import ts from 'typescript-eslint';
import prettier from 'eslint-config-prettier/flat';

export default ts.config(
  { ignores: ['dist/**'] },
  ...ts.configs.recommended,
  ...vue.configs['flat/recommended'],
  { files: ['**/*.vue'], languageOptions: { parserOptions: { parser: ts.parser } } },
  // Prettier owns formatting; ESLint still checks Vue and TypeScript correctness.
  prettier,
);
