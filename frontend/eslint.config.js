import babelParser from '@babel/eslint-parser'
import js from '@eslint/js'
import reactHooks from 'eslint-plugin-react-hooks'
import { reactRefresh } from 'eslint-plugin-react-refresh'
import globals from 'globals'

export default [
  {
    ignores: ['coverage', 'dist', 'node_modules'],
  },
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      globals: {
        ...globals.browser,
        ...globals.node,
      },
      parser: babelParser,
      parserOptions: {
        babelOptions: {
          parserOpts: {
            plugins: ['jsx'],
          },
          presets: [
            [
              '@babel/preset-typescript',
              {
                ignoreExtensions: true,
              },
            ],
          ],
        },
        requireConfigFile: false,
        sourceType: 'module',
      },
    },
    plugins: {
      'react-hooks': reactHooks,
    },
    rules: {
      ...js.configs.recommended.rules,
      ...reactHooks.configs.flat.recommended.rules,
      'no-undef': 'off',
      'no-unused-vars': 'off',
    },
  },
  {
    ...reactRefresh.configs.vite(),
    files: ['src/**/*.{ts,tsx}'],
  },
]
