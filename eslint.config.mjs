import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: [
      '**/dist/**',
      '**/node_modules/**',
      '**/*.js',
      '**/__tests__/**',
      '**/*.test.ts',
      'apps/**',
      'Dockerfile.ts',
    ],
  },
  ...tseslint.configs.recommended,
  {
    files: ['packages/*/src/**/*.ts'],
    rules: {
      '@typescript-eslint/no-floating-promises': 'error',
      '@typescript-eslint/no-misused-promises': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
    },
    languageOptions: {
      parserOptions: {
        projectService: true,
      },
    },
  },
);
