// 由 tools/generate-home-file-icons.cjs 从 Material Icon Theme 5.40.0 生成；勿手改。
package com.privateplayer.feature.home.fileicons

import com.privateplayer.feature.home.R

internal object MaterialIconAssociations {
    const val MAX_SUFFIX_PARTS = 3
    const val MAX_SUFFIX_LENGTH = 25

    fun named(key: String, path: String, light: Boolean): Int = when (key.hashCode() and 15) {
        0 -> named0(key, path, light)
        1 -> named1(key, path, light)
        2 -> named2(key, path, light)
        3 -> named3(key, path, light)
        4 -> named4(key, path, light)
        5 -> named5(key, path, light)
        6 -> named6(key, path, light)
        7 -> named7(key, path, light)
        8 -> named8(key, path, light)
        9 -> named9(key, path, light)
        10 -> named10(key, path, light)
        11 -> named11(key, path, light)
        12 -> named12(key, path, light)
        13 -> named13(key, path, light)
        14 -> named14(key, path, light)
        else -> named15(key, path, light)
    }

    private fun named0(key: String, path: String, light: Boolean): Int = when (key) {
        ".babel-plugin-macrosrc.json5",
        ".babelrc.ts",
        "babel-plugin-macros.config.cjs",
        "babel-plugin-macros.config.mts",
        "babel.config.json",
        "babel.config.yml" -> R.drawable.home_mit_babel
        ".coderabbit.yaml" -> R.drawable.home_mit_coderabbit_ai
        ".copilotignore" -> if (light) R.drawable.home_mit_copilot_light else R.drawable.home_mit_copilot
        ".cracorc.json5",
        "craco.config.cjs",
        "craco.config.mts" -> R.drawable.home_mit_craco
        ".cz.yaml" -> R.drawable.home_mit_commitizen
        ".env.schema" -> R.drawable.home_mit_tune
        ".envrc",
        "zshrc",
        "zshrc_apple_terminal" -> R.drawable.home_mit_console
        ".eslintrc" -> R.drawable.home_mit_eslint
        ".gitpreserve" -> R.drawable.home_mit_git
        ".graphqlrc.cts",
        "graphql.config.js" -> R.drawable.home_mit_graphql
        ".hadolint.yaml" -> R.drawable.home_mit_hadolint
        ".happo.mjs" -> R.drawable.home_mit_happo
        ".hhconfig" -> R.drawable.home_mit_hack
        ".histoire.ts" -> R.drawable.home_mit_histoire
        ".huskyrc.js",
        "husky.config.cts" -> R.drawable.home_mit_husky
        ".jshintignore" -> R.drawable.home_mit_settings
        ".lefthook-local.json",
        ".lefthook-local.yml",
        ".lefthook.toml",
        "lefthook-local.toml" -> R.drawable.home_mit_lefthook
        ".lintstagedrc.mjs" -> R.drawable.home_mit_lintstaged
        ".nuspec" -> R.drawable.home_mit_nuget
        ".postcssrc.json",
        ".postcssrc.yml",
        "postcss.config.ts" -> R.drawable.home_mit_postcss
        ".prettierrc.cjs",
        ".prettierrc.mts",
        "prettier.config.ts" -> R.drawable.home_mit_prettier
        ".puppeteerrc.json5",
        "puppeteer.config.cjs",
        "puppeteer.config.mts" -> R.drawable.home_mit_puppeteer
        ".remarkignore" -> R.drawable.home_mit_remark
        ".rhistory" -> R.drawable.home_mit_r
        ".rubocop.yml" -> if (light) R.drawable.home_mit_rubocop_light else R.drawable.home_mit_rubocop
        ".stylelintrc.js",
        "stylelint.config.cts" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        ".svgrrc.ts",
        "svgr.config.cjs",
        "svgr.config.mts" -> R.drawable.home_mit_svgr
        ".swift-version" -> R.drawable.home_mit_swift
        ".syncpackrc.jsonc",
        "syncpack.config.cts" -> R.drawable.home_mit_syncpack
        ".textlintignore" -> R.drawable.home_mit_textlint
        "_sc_" -> R.drawable.home_mit_godot_assets
        "bashly-strings.yml" -> if (hasIconParent(path, "src")) if (light) R.drawable.home_mit_bashly_strings_light else R.drawable.home_mit_bashly_strings else 0
        "code_of_conduct.txt" -> R.drawable.home_mit_conduct
        "commitlint.config.json",
        "commitlint.config.yml" -> R.drawable.home_mit_commitlint
        "compose.production.yaml",
        "docker-compose.alpha.yaml",
        "docker-compose.override.yaml",
        "docker-compose.stage.yaml",
        "docker-compose.yaml",
        "dockerfile.worker" -> R.drawable.home_mit_docker
        "contentlayer.config.mjs" -> R.drawable.home_mit_contentlayer
        "dune-workspace" -> R.drawable.home_mit_dune
        "esbuild.dev.js" -> R.drawable.home_mit_esbuild
        "eslintrc.cjs",
        "eslintrc.mts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_eslint else 0
        "garden.yaml" -> R.drawable.home_mit_garden
        "gatsby-ssr.tsx" -> R.drawable.home_mit_gatsby
        "gemini.md" -> R.drawable.home_mit_gemini_ai
        "gradlew" -> R.drawable.home_mit_gradle
        "gridsome.server.js" -> R.drawable.home_mit_gridsome
        "gruntfile.cts" -> R.drawable.home_mit_grunt
        "hgrc" -> R.drawable.home_mit_mercurial
        "hobofile",
        "puppetfile" -> R.drawable.home_mit_ruby
        "huskyrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_husky else 0
        "instructions.md" -> R.drawable.home_mit_instructions
        "jest.e2e.json" -> R.drawable.home_mit_jest
        "k8s.yml",
        "kubernetes.yml" -> R.drawable.home_mit_kubernetes
        "metro.config.js" -> R.drawable.home_mit_metro
        "next.config.ts" -> if (light) R.drawable.home_mit_next_light else R.drawable.home_mit_next
        "now.json" -> if (light) R.drawable.home_mit_vercel_light else R.drawable.home_mit_vercel
        "nuxt.config.ts" -> R.drawable.home_mit_nuxt
        "owners" -> R.drawable.home_mit_codeowners
        "package.json" -> R.drawable.home_mit_nodejs
        "payload.config.mts" -> if (light) R.drawable.home_mit_payload_light else R.drawable.home_mit_payload
        "pdm.lock" -> R.drawable.home_mit_pdm
        "plastic.wktree" -> R.drawable.home_mit_plastic
        "playwright.config.base.mjs",
        "playwright.config.js" -> R.drawable.home_mit_playwright
        "posthtml.config.yaml" -> R.drawable.home_mit_posthtml
        "posthtmlrc.cts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_posthtml else 0
        "readme.txt" -> R.drawable.home_mit_readme
        "release-plz.toml" -> R.drawable.home_mit_rocket
        "releaserc.mjs",
        "releaserc.toml" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release else 0
        "remix.config.js" -> if (light) R.drawable.home_mit_remix_light else R.drawable.home_mit_remix
        "rollup-config.js",
        "rollup.config.base.js",
        "rollup.config.mjs",
        "rollup.config.prod.ts" -> R.drawable.home_mit_rollup
        "rslib.config.cts",
        "rslint.config.cts",
        "rstest.config.ts" -> R.drawable.home_mit_rstack
        "scsub" -> if (light) R.drawable.home_mit_scons_light else R.drawable.home_mit_scons
        "security" -> R.drawable.home_mit_lock
        "snapcraft.yml" -> R.drawable.home_mit_snapcraft
        "snowpack.deps.json" -> if (light) R.drawable.home_mit_snowpack_light else R.drawable.home_mit_snowpack
        "stryker.conf.cjs" -> R.drawable.home_mit_stryker
        "stylelintrc.yaml" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint else 0
        "syncpackrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_syncpack else 0
        "tailwind.config.mjs" -> R.drawable.home_mit_tailwindcss
        "taze.config.mjs" -> R.drawable.home_mit_taze
        "trigger.config.mjs" -> R.drawable.home_mit_trigger
        "tsconfig.main.json" -> R.drawable.home_mit_tsconfig
        "verdaccio.yml" -> R.drawable.home_mit_verdaccio
        "vite.config.cts" -> R.drawable.home_mit_vite
        "vitest.workspace.cts" -> R.drawable.home_mit_vitest
        "vpkg.json" -> R.drawable.home_mit_vlang
        "webpack.base.cjs",
        "webpack.base.mts",
        "webpack.client.cts",
        "webpack.common.cts",
        "webpack.config.base.babel.mjs",
        "webpack.config.base.cts",
        "webpack.config.common.babel.cjs",
        "webpack.config.common.babel.mts",
        "webpack.config.prod.cjs",
        "webpack.config.prod.mts",
        "webpack.config.staging.babel.cts",
        "webpack.config.test.ts",
        "webpack.dist.js",
        "webpack.prod.config.mjs",
        "webpack.prod.mjs",
        "webpack.ts" -> R.drawable.home_mit_webpack
        "werf-giterminism.yaml",
        "werf-includes.yml" -> R.drawable.home_mit_werf
        "wxt.config.ts" -> R.drawable.home_mit_wxt
        "xmake.lua" -> R.drawable.home_mit_xmake
        "zizmor.yaml" -> R.drawable.home_mit_zizmor
        else -> 0
    }

    private fun named1(key: String, path: String, light: Boolean): Int = when (key) {
        ".babel-plugin-macrosrc",
        ".babelrc.cts",
        "babel-plugin-macros.config.json5",
        "babel.config.js" -> R.drawable.home_mit_babel
        ".blitz.config.compiled.js" -> R.drawable.home_mit_blitz
        ".clang-format" -> R.drawable.home_mit_settings
        ".clangd" -> R.drawable.home_mit_clangd
        ".commitlintrc.mjs",
        "commitlint.config.js" -> R.drawable.home_mit_commitlint
        ".cracorc",
        "craco.config.json5" -> R.drawable.home_mit_craco
        ".cz.json",
        "cz.toml",
        "cz.yml" -> R.drawable.home_mit_commitizen
        ".editorconfig-checker.json" -> R.drawable.home_mit_editorconfig
        ".env.defaults",
        ".env.test",
        ".env.testing" -> R.drawable.home_mit_tune
        ".env.sentry-build-plugin",
        "sentry.client.config.js" -> R.drawable.home_mit_sentry
        ".eslintignore" -> R.drawable.home_mit_eslint
        ".graphqlrc.ts",
        "graphql.config.toml",
        "graphql.config.yml" -> R.drawable.home_mit_graphql
        ".histoire.cts" -> R.drawable.home_mit_histoire
        ".huskyrc.toml",
        ".huskyrc.yml",
        "husky.config.ts" -> R.drawable.home_mit_husky
        ".modernizrrc.json" -> R.drawable.home_mit_modernizr
        ".phpunit.result.cache" -> R.drawable.home_mit_phpunit
        ".postcssrc.js",
        "postcss.config.cts" -> R.drawable.home_mit_postcss
        ".prettierrc.json5",
        "prettier.config.cts" -> R.drawable.home_mit_prettier
        ".pubignore" -> R.drawable.home_mit_dart
        ".puppeteerrc",
        "puppeteer.config.json5" -> R.drawable.home_mit_puppeteer
        ".sonarcloud.properties" -> R.drawable.home_mit_sonarcloud
        ".stylelintrc.toml",
        ".stylelintrc.yml",
        "stylelint.config.ts" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        ".svgrrc.cts",
        "svgr.config.json5" -> R.drawable.home_mit_svgr
        ".syncpackrc.cjs",
        ".syncpackrc.mts",
        "syncpack.config.ts" -> R.drawable.home_mit_syncpack
        ".taskrc.yaml" -> R.drawable.home_mit_taskfile
        "angular-cli.json" -> R.drawable.home_mit_angular
        "changelog.md" -> R.drawable.home_mit_changelog
        "compose.testing.yaml",
        "containerfile.beta",
        "docker-compose.testing.yml",
        "docker-compose.worker.yml",
        "dockerfile.windows" -> R.drawable.home_mit_docker
        "cypress.config.cjs",
        "cypress.config.mts" -> R.drawable.home_mit_cypress
        "deliverfile",
        "guardfile",
        "matchfile",
        "podfile",
        "rantfile" -> R.drawable.home_mit_ruby
        "drizzle.config.ts" -> R.drawable.home_mit_drizzle
        "eslintrc.json5" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_eslint else 0
        "gridsome.config.js" -> R.drawable.home_mit_gridsome
        "gruntfile.ts" -> R.drawable.home_mit_grunt
        "hadolint.yml" -> R.drawable.home_mit_hadolint
        "huskyrc.json" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_husky else 0
        "jest-e2e.config.mjs",
        "jest.teardown.js" -> R.drawable.home_mit_jest
        "karma-main.js" -> R.drawable.home_mit_karma
        "kcl.yaml" -> R.drawable.home_mit_kcl
        "knip.config.js" -> R.drawable.home_mit_knip
        "latexmkrc" -> R.drawable.home_mit_latexmk
        "licence",
        "license" -> R.drawable.home_mit_license
        "lynx.config.mjs" -> R.drawable.home_mit_lynx
        "nuget.config" -> R.drawable.home_mit_nuget
        "oxfmt.config.ts" -> R.drawable.home_mit_oxc
        "panda.config.js" -> R.drawable.home_mit_panda
        "plopfile.mjs" -> R.drawable.home_mit_plop
        "postcssrc.jsonc",
        "postcssrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_postcss else 0
        "posthtml.config.json" -> R.drawable.home_mit_posthtml
        "posthtmlrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_posthtml else 0
        "pre-commit" -> R.drawable.home_mit_console
        "prettierrc.jsonc",
        "prettierrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_prettier else 0
        "prisma7.config.mjs" -> R.drawable.home_mit_prisma
        "protractor.config.js" -> R.drawable.home_mit_protractor
        "release.config.mjs" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        "release.toml" -> R.drawable.home_mit_rocket
        "rollup.config.prod.vendor.mjs" -> R.drawable.home_mit_rollup
        "rome.json" -> R.drawable.home_mit_rome
        "rsbuild.config.cjs",
        "rsbuild.config.mts",
        "rslib.config.ts",
        "rslint.config.ts",
        "rslint.jsonc",
        "rspack.config.js",
        "rstest.config.cts" -> R.drawable.home_mit_rstack
        "serverless.ts" -> R.drawable.home_mit_serverless
        "stylelintrc.json" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint else 0
        "syncpackrc.json" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_syncpack else 0
        "tsconfig.webworker.json" -> R.drawable.home_mit_tsconfig
        "unocss.config.mts" -> R.drawable.home_mit_unocss
        "velite.config.cjs",
        "velite.config.mts" -> R.drawable.home_mit_velite
        "vite.config.ts" -> R.drawable.home_mit_vite
        "vitest.unit.config.cjs",
        "vitest.unit.config.mts",
        "vitest.workspace.ts" -> R.drawable.home_mit_vitest
        "webpack.client.ts",
        "webpack.common.ts",
        "webpack.config.base.ts",
        "webpack.config.cjs",
        "webpack.config.client.js",
        "webpack.config.common.js",
        "webpack.config.dev.babel.js",
        "webpack.config.mts",
        "webpack.config.staging.babel.ts",
        "webpack.config.test.cts",
        "webpack.config.vendor.cjs",
        "webpack.config.vendor.mts",
        "webpack.cts",
        "webpack.test.cjs",
        "webpack.test.mts",
        "webpackfile.mjs" -> R.drawable.home_mit_webpack
        "wxt.config.cts" -> R.drawable.home_mit_wxt
        else -> 0
    }

    private fun named2(key: String, path: String, light: Boolean): Int = when (key) {
        ".buildignore",
        ".conf" -> R.drawable.home_mit_settings
        ".commitlint.yml",
        "commitlint.config.toml" -> R.drawable.home_mit_commitlint
        ".cursor.json" -> if (light) R.drawable.home_mit_cursor_light else R.drawable.home_mit_cursor
        ".env.prod.example" -> R.drawable.home_mit_tune
        ".esmrc" -> R.drawable.home_mit_nodejs
        ".git-for-windows-updater" -> R.drawable.home_mit_git
        ".k8s.yml" -> R.drawable.home_mit_kubernetes
        ".lefthook-local.toml",
        ".lefthook.yml",
        "lefthook-local.yml" -> R.drawable.home_mit_lefthook
        ".lintstagedrc.yml" -> R.drawable.home_mit_lintstaged
        ".markdownlintignore" -> R.drawable.home_mit_markdownlint
        ".mincloudrc" -> R.drawable.home_mit_ifanr_cloud
        ".modernizrrc.js" -> R.drawable.home_mit_modernizr
        ".nuxtrc" -> R.drawable.home_mit_nuxt
        ".packshiprc.json",
        "packship.config.ts" -> R.drawable.home_mit_packship
        ".php-cs-fixer.php" -> R.drawable.home_mit_php_cs_fixer
        ".postcssrc.toml" -> R.drawable.home_mit_postcss
        ".posthtmlrc.mjs",
        "posthtml.config.js",
        "posthtml.config.jsonc" -> R.drawable.home_mit_posthtml
        ".prettierrc" -> R.drawable.home_mit_prettier
        ".releaserc.mjs" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        ".syncpackrc.json5",
        ".syncpackrc.yaml" -> R.drawable.home_mit_syncpack
        ".umirc.cjs",
        ".umirc.mts" -> R.drawable.home_mit_umi
        ".xcode-version" -> R.drawable.home_mit_swift
        ".yarn-integrity",
        ".yarnrc.yaml" -> R.drawable.home_mit_yarn
        "apkbuild",
        "pkgbuild" -> R.drawable.home_mit_console
        "architecture.md" -> R.drawable.home_mit_architecture
        "azure-pipelines-main.yaml",
        "azure-pipelines.yaml" -> R.drawable.home_mit_azure_pipelines
        "babel.config.toml" -> R.drawable.home_mit_babel
        "babelrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "blitz.config.ts" -> R.drawable.home_mit_blitz
        "changes.md" -> R.drawable.home_mit_changelog
        "cheffile" -> R.drawable.home_mit_ruby
        "commitlintrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_commitlint else 0
        "compose.override.yml",
        "compose.prod.yaml",
        "compose.web.yml",
        "dockerfile.beta",
        "dockerfile.web" -> R.drawable.home_mit_docker
        "copying.rst" -> R.drawable.home_mit_license
        "esbuild.prod.js" -> R.drawable.home_mit_esbuild
        "eslintrc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_eslint else 0
        "excalidraw" -> R.drawable.home_mit_excalidraw
        "gatsby-ssr.js" -> R.drawable.home_mit_gatsby
        "graphqlrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_graphql else 0
        "gruntfile.babel.coffee" -> R.drawable.home_mit_grunt
        "gulpfile.cts" -> R.drawable.home_mit_gulp
        "huskyrc.js",
        "huskyrc.jsonc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_husky else 0
        "jenkinsfile" -> R.drawable.home_mit_jenkins
        "jest.config.cjs",
        "jest.config.json",
        "jest.config.mts",
        "jest.e2e.config.mjs",
        "jest.setup.ts" -> R.drawable.home_mit_jest
        "markdoc.config.js" -> R.drawable.home_mit_markdoc_config
        "milestones.md" -> R.drawable.home_mit_roadmap
        "playwright-ct.config.ts" -> R.drawable.home_mit_playwright
        "pnpm-workspace.yaml" -> if (light) R.drawable.home_mit_pnpm_light else R.drawable.home_mit_pnpm
        "postcssrc.cjs",
        "postcssrc.json",
        "postcssrc.mts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_postcss else 0
        "prettierrc.cjs",
        "prettierrc.json",
        "prettierrc.mts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_prettier else 0
        "procfile" -> R.drawable.home_mit_heroku
        "project.graphcool" -> R.drawable.home_mit_graphcool
        "protractor.conf.coffee" -> R.drawable.home_mit_protractor
        "quasar.conf.ts" -> R.drawable.home_mit_quasar
        "releaserc.yml" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release else 0
        "requirements.txt" -> R.drawable.home_mit_python_misc
        "rslint.json",
        "rspress.config.cts",
        "rstack.config.mjs" -> R.drawable.home_mit_rstack
        "security.txt" -> R.drawable.home_mit_lock
        "sentry.edge.config.cjs",
        "sentry.edge.config.mts",
        "sentry.server.config.cts" -> R.drawable.home_mit_sentry
        "snowpack.config.cjs",
        "snowpack.config.json",
        "snowpack.config.mts" -> if (light) R.drawable.home_mit_snowpack_light else R.drawable.home_mit_snowpack
        "sonar-project.properties" -> R.drawable.home_mit_sonarcloud
        "stylelintrc.js",
        "stylelintrc.jsonc" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint else 0
        "svelte.config.js" -> R.drawable.home_mit_svelte
        "svgo.config.js" -> R.drawable.home_mit_svgo
        "syncpackrc.js",
        "syncpackrc.jsonc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_syncpack else 0
        "taskfile.dist.yaml" -> R.drawable.home_mit_taskfile
        "tsconfig.app.json",
        "tsconfig.prod.json" -> R.drawable.home_mit_tsconfig
        "uno.config.mjs" -> R.drawable.home_mit_unocss
        "vitest.config.ts" -> R.drawable.home_mit_vitest
        "vue.config.mjs" -> R.drawable.home_mit_vue_config
        "webpack.config.dev.mjs",
        "webpack.config.main.cjs",
        "webpack.config.main.mts",
        "webpack.config.prod.babel.cts",
        "webpack.config.production.cjs",
        "webpack.config.production.mts",
        "webpack.config.renderer.cts",
        "webpack.config.server.cts",
        "webpack.config.vendor.production.cjs",
        "webpack.config.vendor.production.mts",
        "webpack.dev.cjs",
        "webpack.dev.mts",
        "webpack.development.mjs",
        "webpack.mix.ts",
        "webpack.production.mjs",
        "webpack.server.cjs",
        "webpack.server.mts" -> R.drawable.home_mit_webpack
        else -> 0
    }

    private fun named3(key: String, path: String, light: Boolean): Int = when (key) {
        ".cfignore" -> R.drawable.home_mit_cloudfoundry
        ".commitlintrc.yml",
        "commitlint.config.jsonc" -> R.drawable.home_mit_commitlint
        ".cursorrules" -> if (light) R.drawable.home_mit_cursor_light else R.drawable.home_mit_cursor
        ".cz.toml" -> R.drawable.home_mit_commitizen
        ".deepsource.toml" -> R.drawable.home_mit_deepsource
        ".dev.vars",
        ".env.dev.local",
        ".env.prod.local" -> R.drawable.home_mit_tune
        ".eslintcache",
        ".eslintrc-md.js",
        "eslint.config.cts" -> R.drawable.home_mit_eslint
        ".gitmessage",
        ".gitmodules",
        ".keep" -> R.drawable.home_mit_git
        ".latexmkrc" -> R.drawable.home_mit_latexmk
        ".lighthouserc.yml" -> R.drawable.home_mit_lighthouse
        ".nuxtignore" -> R.drawable.home_mit_nuxt
        ".packshiprc.js",
        "packship.config.mjs" -> R.drawable.home_mit_packship
        ".pnpmfile.cjs" -> if (light) R.drawable.home_mit_pnpm_light else R.drawable.home_mit_pnpm
        ".postcssrc.jsonc" -> R.drawable.home_mit_postcss
        ".posthtmlrc.ts",
        "posthtml.config.cjs",
        "posthtml.config.mts",
        "posthtml.config.toml" -> R.drawable.home_mit_posthtml
        ".pre-commit-hooks.yaml" -> R.drawable.home_mit_pre_commit
        ".prettierignore",
        ".prettierrc.yaml" -> R.drawable.home_mit_prettier
        ".qa-mincloudrc" -> R.drawable.home_mit_ifanr_cloud
        ".releaserc.ts",
        "release.config.yml" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        ".ruby-version",
        "berksfile",
        "thorfile" -> R.drawable.home_mit_ruby
        ".syncpackrc",
        ".syncpackrc.json" -> R.drawable.home_mit_syncpack
        ".umirc.js" -> R.drawable.home_mit_umi
        ".yarnrc" -> R.drawable.home_mit_yarn
        "ace" -> R.drawable.home_mit_adonis
        "architecture" -> R.drawable.home_mit_architecture
        "babel-plugin-macros.config.yaml",
        "babel.config.jsonc" -> R.drawable.home_mit_babel
        "babel-plugin-macrosrc.cts",
        "babelrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "changes" -> R.drawable.home_mit_changelog
        "cmakecache.txt",
        "cmakepresets.json" -> R.drawable.home_mit_cmake
        "commitlintrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_commitlint else 0
        "compose.alpha.yaml",
        "compose.ci.yaml",
        "compose.dev.yml",
        "compose.stage.yaml",
        "compose.yaml",
        "containerfile.test",
        "containerfile.web",
        "docker-compose.beta.yml",
        "docker-compose.development.yaml",
        "docker-compose.local.yaml",
        "docker-compose.production.yaml",
        "docker-compose.staging.yaml",
        "dockerfile.dev" -> R.drawable.home_mit_docker
        "craco.config.yaml" -> R.drawable.home_mit_craco
        "cracorc.cts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_craco else 0
        "dependabot.yaml" -> R.drawable.home_mit_dependabot
        "drizzle.config.dev.json" -> R.drawable.home_mit_drizzle
        "esbuild.prod.cjs",
        "esbuild.prod.mts" -> R.drawable.home_mit_esbuild
        "eslintrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_eslint else 0
        "graphqlrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_graphql else 0
        "huskyrc.cjs",
        "huskyrc.mts",
        "huskyrc.toml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_husky else 0
        "jest.config.js",
        "jest.e2e.config.ts" -> R.drawable.home_mit_jest
        "kbuild" -> R.drawable.home_mit_makefile
        "knip.ts" -> R.drawable.home_mit_knip
        "liara.json" -> R.drawable.home_mit_liara
        "licence-lgpl",
        "licence.txt",
        "license-lgpl",
        "license.txt" -> R.drawable.home_mit_license
        "markdoc.config.cjs",
        "markdoc.config.mts" -> R.drawable.home_mit_markdoc_config
        "nuget.exe" -> R.drawable.home_mit_nuget
        "pipfile" -> R.drawable.home_mit_python_misc
        "playwright-ct.config.mjs" -> R.drawable.home_mit_playwright
        "postcssrc.js",
        "postcssrc.json5" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_postcss else 0
        "prettierrc.js",
        "prettierrc.json5" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_prettier else 0
        "puppeteer.config.yaml" -> R.drawable.home_mit_puppeteer
        "puppeteerrc.cts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_puppeteer else 0
        "readme.rst" -> R.drawable.home_mit_readme
        "routes.ts" -> R.drawable.home_mit_routing
        "rstack.config.ts" -> R.drawable.home_mit_rstack
        "sentry.edge.config.js" -> R.drawable.home_mit_sentry
        "snowpack.config.js" -> if (light) R.drawable.home_mit_snowpack_light else R.drawable.home_mit_snowpack
        "sonarcloud.yaml" -> R.drawable.home_mit_sonarcloud
        "stencil.config.js" -> R.drawable.home_mit_stencil
        "stylelintrc.cjs",
        "stylelintrc.mts",
        "stylelintrc.toml" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint else 0
        "supabase.js",
        "supabase.py" -> R.drawable.home_mit_supabase
        "svelte.config.cjs",
        "svelte.config.mts" -> R.drawable.home_mit_svelte
        "svgo.config.cjs" -> R.drawable.home_mit_svgo
        "svgr.config.yaml" -> R.drawable.home_mit_svgr
        "svgrrc.cts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_svgr else 0
        "syncpackrc.cjs",
        "syncpackrc.mts",
        "syncpackrc.toml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_syncpack else 0
        "tauri.conf.json" -> R.drawable.home_mit_tauri
        "timeline.txt" -> R.drawable.home_mit_roadmap
        "tsdown.config.cts" -> R.drawable.home_mit_tsdown
        "uno.config.ts" -> R.drawable.home_mit_unocss
        "vitest.config.mjs" -> R.drawable.home_mit_vitest
        "vue.config.ts" -> R.drawable.home_mit_vue_config
        "wallaby.conf.js" -> R.drawable.home_mit_wallaby
        "webpack.config.babel.cts",
        "webpack.config.dev.ts",
        "webpack.config.main.js",
        "webpack.config.production.js",
        "webpack.config.vendor.production.js",
        "webpack.dev.js",
        "webpack.development.ts",
        "webpack.mix.mjs",
        "webpack.production.ts",
        "webpack.server.js" -> R.drawable.home_mit_webpack
        "werf-includes.lock",
        "werf.yaml" -> R.drawable.home_mit_werf
        "windi.config.cts" -> R.drawable.home_mit_windicss
        "zeabur.yml" -> if (light) R.drawable.home_mit_zeabur_light else R.drawable.home_mit_zeabur
        else -> 0
    }

    private fun named4(key: String, path: String, light: Boolean): Int = when (key) {
        ".aiexclude" -> R.drawable.home_mit_gemini_ai
        ".appveyor.yml" -> R.drawable.home_mit_appveyor
        ".babel-plugin-macrosrc.yaml",
        "babel-plugin-macros.config.json",
        "babel.config.cjs",
        "babel.config.mts" -> R.drawable.home_mit_babel
        ".bazelversion" -> R.drawable.home_mit_bazel
        ".biome.json" -> R.drawable.home_mit_biome
        ".clang-tidy" -> R.drawable.home_mit_settings
        ".codeclimate.yml" -> if (light) R.drawable.home_mit_code_climate_light else R.drawable.home_mit_code_climate
        ".commitlintrc.ts",
        "commitlint.config.cjs",
        "commitlint.config.mts" -> R.drawable.home_mit_commitlint
        ".cracorc.yaml",
        "craco.config.json" -> R.drawable.home_mit_craco
        ".cursor" -> if (light) R.drawable.home_mit_cursor_light else R.drawable.home_mit_cursor
        ".drone.yml" -> if (light) R.drawable.home_mit_drone_light else R.drawable.home_mit_drone
        ".env.production.example",
        ".env.stg.local" -> R.drawable.home_mit_tune
        ".eslintrc.cts" -> R.drawable.home_mit_eslint
        ".git",
        ".git-blame-ignore-revs",
        ".gitinclude",
        "git-rebase-todo" -> R.drawable.home_mit_git
        ".graphqlrc.mjs",
        "graphql.config.jsonc" -> R.drawable.home_mit_graphql
        ".huskyrc.jsonc",
        "husky.config.mjs" -> R.drawable.home_mit_husky
        ".oxfmtrc.jsonc" -> R.drawable.home_mit_oxc
        ".packshiprc" -> R.drawable.home_mit_packship
        ".postcssrc.cjs",
        ".postcssrc.mts" -> R.drawable.home_mit_postcss
        ".posthtmlrc.yml",
        "posthtml.config.json5" -> R.drawable.home_mit_posthtml
        ".prettierrc.json" -> R.drawable.home_mit_prettier
        ".puppeteerrc.yaml",
        "puppeteer.config.json" -> R.drawable.home_mit_puppeteer
        ".releaserc.yml",
        "release.config.ts" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        ".sentryclirc",
        "sentry.client.config.cjs",
        "sentry.client.config.mts" -> R.drawable.home_mit_sentry
        ".stylelintrc.jsonc",
        "stylelint.config.mjs" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        ".syncpackrc.js",
        "syncpack.config.mjs" -> R.drawable.home_mit_syncpack
        "angular.json",
        "ng-package.json" -> R.drawable.home_mit_angular
        "bashly.yaml" -> if (hasIconParent(path, "src")) R.drawable.home_mit_bashly else 0
        "bun.lockb" -> if (light) R.drawable.home_mit_bun_light else R.drawable.home_mit_bun
        "changelog" -> R.drawable.home_mit_changelog
        "compose.worker.yml",
        "containerfile.dev",
        "dockerfile.test" -> R.drawable.home_mit_docker
        "cypress.config.js" -> R.drawable.home_mit_cypress
        "deno.json" -> if (light) R.drawable.home_mit_deno_light else R.drawable.home_mit_deno
        "drizzle.config.dev.js",
        "drizzle.config.prod.ts" -> R.drawable.home_mit_drizzle
        "dune-project" -> R.drawable.home_mit_dune
        "ecosystem.config.cts" -> R.drawable.home_mit_pm2_ecosystem
        "esbuild.config.cts",
        "esbuild.cts",
        "esbuild.stage.cts",
        "esbuild.test.cts" -> R.drawable.home_mit_esbuild
        "eslintrc.json" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_eslint else 0
        "excalidraw.json" -> R.drawable.home_mit_excalidraw
        "firestore.rules" -> R.drawable.home_mit_firebase
        "go.work.sum" -> R.drawable.home_mit_go_mod
        "google-services.json" -> R.drawable.home_mit_google
        "gradle-wrapper.properties" -> R.drawable.home_mit_gradle
        "graphqlrc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_graphql else 0
        "huskyrc.json5" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_husky else 0
        "jest-e2e.config.ts" -> R.drawable.home_mit_jest
        "keystatic.config.js",
        "keystatic.config.jsx" -> R.drawable.home_mit_keystatic
        "labeler.yaml" -> if (hasIconParent(path, ".github")) R.drawable.home_mit_label else 0
        "lefthook.yml" -> R.drawable.home_mit_lefthook
        "licence.md",
        "license.md" -> R.drawable.home_mit_license
        "lynx.config.ts" -> R.drawable.home_mit_lynx
        "manifest.in" -> R.drawable.home_mit_python_misc
        "mercurial.ini" -> R.drawable.home_mit_mercurial
        "mocha.opts" -> R.drawable.home_mit_mocha
        "opencode.jsonc" -> if (light) R.drawable.home_mit_opencode_light else R.drawable.home_mit_opencode
        "package-lock.json" -> R.drawable.home_mit_nodejs
        "panda.config.cjs",
        "panda.config.mts" -> R.drawable.home_mit_panda
        "plopfile.ts" -> R.drawable.home_mit_plop
        "poetry.lock" -> R.drawable.home_mit_poetry
        "postcssrc",
        "postcssrc.toml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_postcss else 0
        "posthtmlrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_posthtml else 0
        "pre-push",
        "zlogout" -> R.drawable.home_mit_console
        "prettierrc",
        "prettierrc.toml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_prettier else 0
        "prisma.config.cts",
        "prisma7.config.ts" -> R.drawable.home_mit_prisma
        "project.garden.yml" -> R.drawable.home_mit_garden
        "quasar.config.ts" -> R.drawable.home_mit_quasar
        "rollup.config.prod.vendor.ts" -> R.drawable.home_mit_rollup
        "route.ts",
        "route.tsx",
        "router.ts",
        "router.tsx" -> R.drawable.home_mit_routing
        "rsbuild.config.js",
        "rslib.config.mjs",
        "rslint.config.mjs",
        "rspack.config.cjs",
        "rspack.config.mts" -> R.drawable.home_mit_rstack
        "sconstruct" -> if (light) R.drawable.home_mit_scons_light else R.drawable.home_mit_scons
        "skill.md" -> R.drawable.home_mit_skill
        "stryker.conf.json" -> R.drawable.home_mit_stryker
        "stylelintrc.json5" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint else 0
        "svgr.config.json" -> R.drawable.home_mit_svgr
        "swagger.yml" -> R.drawable.home_mit_swagger
        "syncpackrc.json5" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_syncpack else 0
        "taskfile.yaml" -> R.drawable.home_mit_taskfile
        "timeline.md" -> R.drawable.home_mit_roadmap
        "tsconfig.esm.json",
        "tsconfig.vitest.json" -> R.drawable.home_mit_tsconfig
        "unocss.config.js" -> R.drawable.home_mit_unocss
        "velite.config.js" -> R.drawable.home_mit_velite
        "vite.config.mjs" -> R.drawable.home_mit_vite
        "vitest.unit.config.js",
        "vitest.workspace.mjs" -> R.drawable.home_mit_vitest
        "webpack.client.mjs",
        "webpack.common.mjs",
        "webpack.config.base.mjs",
        "webpack.config.client.cjs",
        "webpack.config.client.mts",
        "webpack.config.common.cjs",
        "webpack.config.common.mts",
        "webpack.config.dev.babel.cjs",
        "webpack.config.dev.babel.mts",
        "webpack.config.js",
        "webpack.config.production.babel.cts",
        "webpack.config.staging.babel.mjs",
        "webpack.config.staging.cts",
        "webpack.config.vendor.js",
        "webpack.test.js",
        "webpackfile.ts" -> R.drawable.home_mit_webpack
        else -> 0
    }

    private fun named5(key: String, path: String, light: Boolean): Int = when (key) {
        ".babel-plugin-macrosrc.cts",
        ".babel-plugin-macrosrc.json",
        ".babelrc.mjs",
        "babel-plugin-macros.config.js",
        "babel.config.json5" -> R.drawable.home_mit_babel
        ".bazelrc" -> R.drawable.home_mit_bazel
        ".cracorc.cts",
        ".cracorc.json",
        "craco.config.js" -> R.drawable.home_mit_craco
        ".cursorrc" -> if (light) R.drawable.home_mit_cursor_light else R.drawable.home_mit_cursor
        ".env.dist",
        ".env.production.local" -> R.drawable.home_mit_tune
        ".eslintrc-jsdoc.js",
        ".eslintrc.yaml",
        "eslint-options.js" -> R.drawable.home_mit_eslint
        ".gardenignore" -> R.drawable.home_mit_garden
        ".gitattributes-global" -> R.drawable.home_mit_git
        ".histoire.mjs",
        "histoire.config.cts" -> R.drawable.home_mit_histoire
        ".huskyrc.cjs",
        ".huskyrc.mts" -> R.drawable.home_mit_husky
        ".knip.jsonc" -> R.drawable.home_mit_knip
        ".markdownlint.yaml" -> R.drawable.home_mit_markdownlint
        ".modernizrrc" -> R.drawable.home_mit_modernizr
        ".mrconfig" -> R.drawable.home_mit_settings
        ".nano-staged.json" -> if (light) R.drawable.home_mit_nano_staged_light else R.drawable.home_mit_nano_staged
        ".nycrc.yml" -> R.drawable.home_mit_istanbul
        ".php_cs" -> R.drawable.home_mit_php_cs_fixer
        ".postcssrc.json5",
        "postcss.config.mjs" -> R.drawable.home_mit_postcss
        ".pre-commit-config.yaml" -> R.drawable.home_mit_pre_commit
        ".prettierrc.js",
        "prettier.config.mjs" -> R.drawable.home_mit_prettier
        ".puppeteerrc.cts",
        ".puppeteerrc.json",
        "puppeteer.config.js" -> R.drawable.home_mit_puppeteer
        ".python-version",
        "celerybeat.pid",
        "py.typed" -> R.drawable.home_mit_python_misc
        ".secrets" -> R.drawable.home_mit_key
        ".semgrepignore" -> R.drawable.home_mit_semgrep
        ".stylelintrc.cjs",
        ".stylelintrc.mts" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        ".svgrrc.mjs",
        "svgr.config.js" -> R.drawable.home_mit_svgr
        ".syncpackrc.toml" -> R.drawable.home_mit_syncpack
        ".travis.yml" -> R.drawable.home_mit_travis
        "architecture.txt" -> R.drawable.home_mit_architecture
        "astro.config.cts" -> R.drawable.home_mit_astro_config
        "auto-config.yaml" -> if (light) R.drawable.home_mit_auto_light else R.drawable.home_mit_auto
        "babelrc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "bitbucket-pipelines.yml" -> R.drawable.home_mit_bitbucket
        "changes.txt" -> R.drawable.home_mit_changelog
        "commitlint.config.json5" -> R.drawable.home_mit_commitlint
        "commitlintrc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_commitlint else 0
        "contentlayer.config.ts" -> R.drawable.home_mit_contentlayer
        "docker-compose.prod.yaml",
        "docker-compose.test.yml",
        "docker-compose.web.yml" -> R.drawable.home_mit_docker
        "esbuild.dev.cjs",
        "esbuild.dev.mts" -> R.drawable.home_mit_esbuild
        "eslintrc.js" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_eslint else 0
        "go.sum" -> R.drawable.home_mit_go_mod
        "graphql.config.cjs",
        "graphql.config.mts" -> R.drawable.home_mit_graphql
        "gruntfile.babel.ts" -> R.drawable.home_mit_grunt
        "huskyrc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_husky else 0
        "jest.preset.json" -> R.drawable.home_mit_jest
        "karma.conf.js" -> R.drawable.home_mit_karma
        "lighthouserc.yml" -> R.drawable.home_mit_lighthouse
        "maven.config" -> R.drawable.home_mit_maven
        "metro.config.cjs",
        "metro.config.mts" -> R.drawable.home_mit_metro
        "milestones.txt" -> R.drawable.home_mit_roadmap
        "next.config.mjs" -> if (light) R.drawable.home_mit_next_light else R.drawable.home_mit_next
        "openapi.yaml" -> if (light) R.drawable.home_mit_openapi_light else R.drawable.home_mit_openapi
        "payload.config.js" -> if (light) R.drawable.home_mit_payload_light else R.drawable.home_mit_payload
        "phpunit-watcher.yml",
        "phpunit.xml" -> R.drawable.home_mit_phpunit
        "playwright.config.base.ts",
        "playwright.config.cjs",
        "playwright.config.mts" -> R.drawable.home_mit_playwright
        "protractor.conf.ts" -> R.drawable.home_mit_protractor
        "rakefile" -> R.drawable.home_mit_ruby
        "releaserc.jsonc",
        "releaserc.ts" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release else 0
        "rolldown.config.cts" -> R.drawable.home_mit_rolldown
        "rollup.config.prod.mjs",
        "rollup.config.ts" -> R.drawable.home_mit_rollup
        "routes.tsx" -> R.drawable.home_mit_routing
        "rstest.config.mjs" -> R.drawable.home_mit_rstack
        "ruff.toml" -> R.drawable.home_mit_ruff
        "security.md" -> R.drawable.home_mit_lock
        "steadybit.yml" -> R.drawable.home_mit_steadybit
        "stryker.conf.js" -> R.drawable.home_mit_stryker
        "stylelintrc" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint else 0
        "syncpackrc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_syncpack else 0
        "tailwind.config.ts",
        "tailwind.ts" -> R.drawable.home_mit_tailwindcss
        "tauri.config.json" -> R.drawable.home_mit_tauri
        "taze.config.ts" -> R.drawable.home_mit_taze
        "trigger.config.ts" -> R.drawable.home_mit_trigger
        "tsconfig.build.json",
        "tsconfig.json",
        "tsconfig.paths.json" -> R.drawable.home_mit_tsconfig
        "vitest.e2e.config.cts" -> R.drawable.home_mit_vitest
        "webpack.base.js",
        "webpack.config.base.babel.ts",
        "webpack.config.common.babel.js",
        "webpack.config.prod.js",
        "webpack.config.test.mjs",
        "webpack.dist.cjs",
        "webpack.dist.mts",
        "webpack.mjs",
        "webpack.prod.config.ts",
        "webpack.prod.ts" -> R.drawable.home_mit_webpack
        "wxt.config.mjs" -> R.drawable.home_mit_wxt
        "yarn-error.log",
        "yarn.lock" -> R.drawable.home_mit_yarn
        else -> 0
    }

    private fun named6(key: String, path: String, light: Boolean): Int = when (key) {
        ".babel-plugin-macrosrc.js",
        "babel-plugin-macros.config.cts",
        "babel-plugin-macros.config.toml" -> R.drawable.home_mit_babel
        ".bazelignore" -> R.drawable.home_mit_bazel
        ".bithoundrc" -> R.drawable.home_mit_bithound
        ".codecov.yaml" -> R.drawable.home_mit_codecov
        ".commitlintrc.jsonc" -> R.drawable.home_mit_commitlint
        ".coverage",
        ".scrapy" -> R.drawable.home_mit_python_misc
        ".cracorc.js",
        "craco.config.cts",
        "craco.config.toml" -> R.drawable.home_mit_craco
        ".cursorignore",
        ".cursorindexingignore" -> if (light) R.drawable.home_mit_cursor_light else R.drawable.home_mit_cursor
        ".czrc" -> R.drawable.home_mit_commitizen
        ".env.dev",
        ".env.prod" -> R.drawable.home_mit_tune
        ".eslintrc.json",
        "eslint.config.yaml" -> R.drawable.home_mit_eslint
        ".firebaserc" -> R.drawable.home_mit_firebase
        ".gitconfig",
        ".gitignore" -> R.drawable.home_mit_git
        ".graphqlrc.yml",
        "graphql.config.json5",
        "graphql.config.ts" -> R.drawable.home_mit_graphql
        ".happo.cjs" -> R.drawable.home_mit_happo
        ".hgtags" -> R.drawable.home_mit_mercurial
        ".hintrc" -> R.drawable.home_mit_webhint
        ".huskyrc.json5",
        ".huskyrc.ts",
        "husky.config.yml" -> R.drawable.home_mit_husky
        ".justfile" -> R.drawable.home_mit_just
        ".lintstagedrc.cjs" -> R.drawable.home_mit_lintstaged
        ".markdownlint-cli2.yaml",
        ".markdownlint.json" -> R.drawable.home_mit_markdownlint
        ".mcignore" -> R.drawable.home_mit_minecraft
        ".nano-staged.js" -> if (light) R.drawable.home_mit_nano_staged_light else R.drawable.home_mit_nano_staged
        ".php-cs-fixer.dist.php" -> R.drawable.home_mit_php_cs_fixer
        ".postcssrc" -> R.drawable.home_mit_postcss
        ".prettierrc.cts",
        ".prettierrc.toml" -> R.drawable.home_mit_prettier
        ".puppeteerrc.js",
        "puppeteer.config.cts",
        "puppeteer.config.toml" -> R.drawable.home_mit_puppeteer
        ".release-it.yaml" -> R.drawable.home_mit_rocket
        ".remarkrc.yaml" -> R.drawable.home_mit_remark
        ".stryker.conf.json",
        ".stryker.config.mjs",
        "stryker.config.json" -> R.drawable.home_mit_stryker
        ".stylelintrc.json5",
        ".stylelintrc.ts",
        "stylelint.config.yml" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        ".textlintrc.yaml" -> R.drawable.home_mit_textlint
        ".whitesource" -> R.drawable.home_mit_json
        "apollo.config.js" -> R.drawable.home_mit_apollo
        "appveyor.yml" -> R.drawable.home_mit_appveyor
        "astro.config.js" -> R.drawable.home_mit_astro_config
        "auto-config.json",
        "auto.config.js" -> if (light) R.drawable.home_mit_auto_light else R.drawable.home_mit_auto
        "babel-plugin-macrosrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "bashly-settings.yaml" -> if (light) R.drawable.home_mit_bashly_settings_light else R.drawable.home_mit_bashly_settings
        "berksfile.lock",
        "snapfile" -> R.drawable.home_mit_ruby
        "biome.json" -> R.drawable.home_mit_biome
        "changelog.txt" -> R.drawable.home_mit_changelog
        "cmakelists.txt" -> R.drawable.home_mit_cmake
        "compose.beta.yml",
        "compose.development.yaml",
        "compose.local.yaml",
        "compose.staging.yaml",
        "docker-compose.ci.yaml",
        "docker-compose.dev.yml" -> R.drawable.home_mit_docker
        "contentlayer.config.cjs",
        "contentlayer.config.mts" -> R.drawable.home_mit_contentlayer
        "cracorc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_craco else 0
        "esbuild.dev.ts" -> R.drawable.home_mit_esbuild
        "eslintrc.cts",
        "eslintrc.toml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_eslint else 0
        "fuse.js" -> R.drawable.home_mit_fusebox
        "gatsby-browser.tsx" -> R.drawable.home_mit_gatsby
        "gulpfile.mjs" -> R.drawable.home_mit_gulp
        "histoire.config.js" -> R.drawable.home_mit_histoire
        "jest-preset.json",
        "jest.preset.js" -> R.drawable.home_mit_jest
        "jsr.jsonc" -> if (light) R.drawable.home_mit_jsr_light else R.drawable.home_mit_jsr
        "licence.rst",
        "license.rst" -> R.drawable.home_mit_license
        "metro.config.ts" -> R.drawable.home_mit_metro
        "nodemon-debug.json" -> R.drawable.home_mit_nodemon
        "nyc.config.cjs" -> R.drawable.home_mit_istanbul
        "openapi.json" -> if (light) R.drawable.home_mit_openapi_light else R.drawable.home_mit_openapi
        "phpstan.dist.neon",
        "phpstan.neon.dist" -> R.drawable.home_mit_phpstan
        "playwright.config.base.cjs",
        "playwright.config.base.mts",
        "playwright.config.ts" -> R.drawable.home_mit_playwright
        "posthtmlrc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_posthtml else 0
        "puppeteerrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_puppeteer else 0
        "release.config.jsonc" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        "releaserc.cjs",
        "releaserc.mts" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release else 0
        "remix.config.ts" -> if (light) R.drawable.home_mit_remix_light else R.drawable.home_mit_remix
        "renovate.json" -> R.drawable.home_mit_renovate
        "rolldown.config.js" -> R.drawable.home_mit_rolldown
        "rollup-config.ts",
        "rollup.config.base.ts",
        "rollup.config.common.js" -> R.drawable.home_mit_rollup
        "rspress.config.mjs" -> R.drawable.home_mit_rstack
        "screwdriver.yml" -> R.drawable.home_mit_screwdriver
        "sentry.server.config.mjs" -> R.drawable.home_mit_sentry
        "serverless.yml" -> R.drawable.home_mit_serverless
        "stitches.config.js" -> if (light) R.drawable.home_mit_stitches_light else R.drawable.home_mit_stitches
        "svgr.config.cts",
        "svgr.config.toml" -> R.drawable.home_mit_svgr
        "svgrrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_svgr else 0
        "syncpack.config.yml" -> R.drawable.home_mit_syncpack
        "tailwind.config.cjs",
        "tailwind.config.mts" -> R.drawable.home_mit_tailwindcss
        "taze.config.cjs",
        "taze.config.mts" -> R.drawable.home_mit_taze
        "trigger.config.cjs",
        "trigger.config.mts" -> R.drawable.home_mit_trigger
        "tsconfig.renderer.json",
        "tsconfig.server.json" -> R.drawable.home_mit_tsconfig
        "vercel.ts" -> if (light) R.drawable.home_mit_vercel_light else R.drawable.home_mit_vercel
        "vitest.e2e.config.js" -> R.drawable.home_mit_vitest
        "webpack.base.cts",
        "webpack.config.base.babel.cjs",
        "webpack.config.base.babel.mts",
        "webpack.config.common.babel.cts",
        "webpack.config.prod.babel.mjs",
        "webpack.config.prod.cts",
        "webpack.config.renderer.mjs",
        "webpack.config.server.mjs",
        "webpack.dist.ts",
        "webpack.prod.cjs",
        "webpack.prod.config.cjs",
        "webpack.prod.config.mts",
        "webpack.prod.mts" -> R.drawable.home_mit_webpack
        "xmake" -> R.drawable.home_mit_xmake
        "zeabur.jsonc" -> if (light) R.drawable.home_mit_zeabur_light else R.drawable.home_mit_zeabur
        else -> 0
    }

    private fun named7(key: String, path: String, light: Boolean): Int = when (key) {
        ".babel-plugin-macrosrc.toml",
        ".babelrc.yml",
        "babel.config.ts" -> R.drawable.home_mit_babel
        ".commitlintrc.cjs",
        ".commitlintrc.mts",
        "commitlint.config.ts" -> R.drawable.home_mit_commitlint
        ".coveragerc" -> R.drawable.home_mit_python_misc
        ".cracorc.toml" -> R.drawable.home_mit_craco
        ".env.stg" -> R.drawable.home_mit_tune
        ".eslintrc.base.json",
        ".eslintrc.js",
        "eslint.config.json",
        "eslint.config.mjs" -> R.drawable.home_mit_eslint
        ".gitattributes_global" -> R.drawable.home_mit_git
        ".huskyrc" -> R.drawable.home_mit_husky
        ".jsbeautifyrc" -> R.drawable.home_mit_json
        ".liaraignore" -> R.drawable.home_mit_liara
        ".lighthouserc.cjs" -> R.drawable.home_mit_lighthouse
        ".mailmap" -> R.drawable.home_mit_email
        ".markdownlint-cli2.mjs" -> R.drawable.home_mit_markdownlint
        ".mocharc.yml" -> R.drawable.home_mit_mocha
        ".phpunit-watcher.yml" -> R.drawable.home_mit_phpunit
        ".postcssrc.ts",
        "postcss.config.yml" -> R.drawable.home_mit_postcss
        ".posthtmlrc.jsonc" -> R.drawable.home_mit_posthtml
        ".puppeteerrc.toml" -> R.drawable.home_mit_puppeteer
        ".release-it.json" -> R.drawable.home_mit_rocket
        ".releaserc.jsonc",
        "release.config.cjs",
        "release.config.mts" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        ".remarkrc.json",
        ".remarkrc.mjs" -> R.drawable.home_mit_remark
        ".renovaterc.json",
        "renovate-config.json" -> R.drawable.home_mit_renovate
        ".rubocop-todo.yml" -> if (light) R.drawable.home_mit_rubocop_light else R.drawable.home_mit_rubocop
        ".ruff.toml" -> R.drawable.home_mit_ruff
        ".shellcheckrc" -> if (light) R.drawable.home_mit_shellcheck_light else R.drawable.home_mit_shellcheck
        ".steadybit.yml" -> R.drawable.home_mit_steadybit
        ".stryker.conf.js",
        "stryker.config.js" -> R.drawable.home_mit_stryker
        ".stylelintrc" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        ".svgrrc.yml" -> R.drawable.home_mit_svgr
        ".syncpackrc.cts" -> R.drawable.home_mit_syncpack
        ".tazerc" -> R.drawable.home_mit_taze
        ".textlintrc.json" -> R.drawable.home_mit_textlint
        ".vercelignore" -> if (light) R.drawable.home_mit_vercel_light else R.drawable.home_mit_vercel
        ".wakatime-project" -> if (light) R.drawable.home_mit_wakatime_light else R.drawable.home_mit_wakatime
        ".yarnclean" -> R.drawable.home_mit_yarn
        "agents.md" -> R.drawable.home_mit_agent
        "androidmanifest.xml" -> R.drawable.home_mit_android
        "auto-config.js" -> if (light) R.drawable.home_mit_auto_light else R.drawable.home_mit_auto
        "babel-plugin-macrosrc.json",
        "babel-plugin-macrosrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "buildkite.yml" -> R.drawable.home_mit_buildkite
        "cabal.project.local" -> R.drawable.home_mit_cabal
        "caddyfile" -> R.drawable.home_mit_caddy
        "claude.md" -> R.drawable.home_mit_claude
        "code_of_conduct.md" -> R.drawable.home_mit_conduct
        "codecov.yml" -> R.drawable.home_mit_codecov
        "compose.test.yaml",
        "containerfile.ci" -> R.drawable.home_mit_docker
        "concourse.yml" -> R.drawable.home_mit_concourse
        "copyright",
        "licence-bsd",
        "licence-gpl",
        "license-bsd",
        "license-gpl" -> R.drawable.home_mit_license
        "cracorc.json",
        "cracorc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_craco else 0
        "cypress.config.cts" -> R.drawable.home_mit_cypress
        "deno.lock" -> if (light) R.drawable.home_mit_deno_light else R.drawable.home_mit_deno
        "dune-workspace.dev" -> R.drawable.home_mit_dune
        "ecosystem.config.js" -> R.drawable.home_mit_pm2_ecosystem
        "esbuild.config.js",
        "esbuild.js",
        "esbuild.stage.js",
        "esbuild.test.js" -> R.drawable.home_mit_esbuild
        "funding.yml" -> if (hasIconParent(path, ".github")) R.drawable.home_mit_github_sponsors else 0
        "gatsby-config.mjs",
        "gatsby-node.mjs" -> R.drawable.home_mit_gatsby
        "go.work" -> R.drawable.home_mit_go_mod
        "gradlew.bat" -> R.drawable.home_mit_gradle
        "graphqlrc.jsonc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_graphql else 0
        "gymfile" -> R.drawable.home_mit_ruby
        "hardhat.config.js" -> R.drawable.home_mit_hardhat
        "instruction.md" -> R.drawable.home_mit_instructions
        "jest-e2e.config.cjs",
        "jest-e2e.config.mts",
        "jest-preset.js" -> R.drawable.home_mit_jest
        "karma-main.ts",
        "karma.config.js" -> R.drawable.home_mit_karma
        "knip.config.ts",
        "knip.jsonc" -> R.drawable.home_mit_knip
        "lint-staged.config.js" -> R.drawable.home_mit_lintstaged
        "lynx.config.cjs",
        "lynx.config.mts" -> R.drawable.home_mit_lynx
        "nano-staged.json",
        "nano-staged.mjs" -> if (light) R.drawable.home_mit_nano_staged_light else R.drawable.home_mit_nano_staged
        "netlify.yml" -> if (light) R.drawable.home_mit_netlify_light else R.drawable.home_mit_netlify
        "panda.config.ts" -> R.drawable.home_mit_panda
        "plastic.workspace" -> R.drawable.home_mit_plastic
        "plopfile.cjs" -> R.drawable.home_mit_plop
        "pom.xml" -> R.drawable.home_mit_maven
        "prettier.config.yml" -> R.drawable.home_mit_prettier
        "prisma.config.js",
        "prisma7.config.cjs",
        "prisma7.config.mts" -> R.drawable.home_mit_prisma
        "procfile.windows" -> R.drawable.home_mit_heroku
        "protractor.config.ts" -> R.drawable.home_mit_protractor
        "puppeteerrc.json",
        "puppeteerrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_puppeteer else 0
        "quasar.config.cjs" -> R.drawable.home_mit_quasar
        "readme.md" -> R.drawable.home_mit_readme
        "releaserc.json5" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release else 0
        "rollup.config.dev.mjs" -> R.drawable.home_mit_rollup
        "rsbuild.config.cts",
        "rspack.config.ts" -> R.drawable.home_mit_rstack
        "sentry.client.config.ts" -> R.drawable.home_mit_sentry
        "svgrrc.json",
        "svgrrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_svgr else 0
        "tsconfig.cjs.json",
        "tsconfig.config.json",
        "tsconfig.node.json",
        "tsconfig.test.json" -> R.drawable.home_mit_tsconfig
        "tsdown.config.json",
        "tsdown.config.mjs" -> R.drawable.home_mit_tsdown
        "uv.toml" -> R.drawable.home_mit_uv
        "velite.config.cts" -> R.drawable.home_mit_velite
        "vitest.unit.config.cts" -> R.drawable.home_mit_vitest
        "webpack.config.babel.mjs",
        "webpack.config.client.ts",
        "webpack.config.coffee",
        "webpack.config.common.ts",
        "webpack.config.cts",
        "webpack.config.dev.babel.ts",
        "webpack.config.production.babel.js",
        "webpack.config.staging.js",
        "webpack.config.vendor.cts",
        "webpack.test.cts",
        "webpackfile.cjs",
        "webpackfile.mts" -> R.drawable.home_mit_webpack
        "windi.config.json" -> R.drawable.home_mit_windicss
        "wrangler.jsonc" -> R.drawable.home_mit_wrangler
        else -> 0
    }

    private fun named8(key: String, path: String, light: Boolean): Int = when (key) {
        ".babelrc.yaml" -> R.drawable.home_mit_babel
        ".browserslistrc" -> if (light) R.drawable.home_mit_browserlist_light else R.drawable.home_mit_browserlist
        ".bun-version" -> if (light) R.drawable.home_mit_bun_light else R.drawable.home_mit_bun
        ".commitlintrc.json5" -> R.drawable.home_mit_commitlint
        ".env.production" -> R.drawable.home_mit_tune
        ".eslintrc.mjs",
        ".eslintrc.toml",
        "eslint.config.js" -> R.drawable.home_mit_eslint
        ".htpasswd" -> R.drawable.home_mit_key
        ".jscsrc" -> R.drawable.home_mit_json
        ".lintstagedrc",
        "lint-staged.config.mjs" -> R.drawable.home_mit_lintstaged
        ".mocharc.yaml" -> R.drawable.home_mit_mocha
        ".nvmrc" -> R.drawable.home_mit_nodejs
        ".oxlintrc.jsonc" -> R.drawable.home_mit_oxc
        ".parcelrc" -> R.drawable.home_mit_parcel
        ".posthtmlrc.cjs",
        ".posthtmlrc.mts",
        "posthtml.config.ts" -> R.drawable.home_mit_posthtml
        ".release-it.js" -> R.drawable.home_mit_rocket
        ".releaserc.cjs",
        ".releaserc.mts",
        "release.config.json5" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        ".remarkrc.js" -> R.drawable.home_mit_remark
        ".replit" -> R.drawable.home_mit_replit
        ".steadybit.yaml" -> R.drawable.home_mit_steadybit
        ".stryker.conf.mjs",
        ".stryker.config.json",
        "stryker.config.mjs" -> R.drawable.home_mit_stryker
        ".stylelintignore" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        ".svgrrc.yaml" -> R.drawable.home_mit_svgr
        ".textlintrc.js" -> R.drawable.home_mit_textlint
        ".umirc.cts" -> R.drawable.home_mit_umi
        ".yardopts",
        "manifest.mf" -> R.drawable.home_mit_settings
        "appwrite.json" -> R.drawable.home_mit_appwrite
        "architecture.rst" -> R.drawable.home_mit_architecture
        "authors",
        "contributors" -> R.drawable.home_mit_authors
        "babel-plugin-macrosrc.js",
        "babelrc.jsonc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "buildkite.yaml" -> R.drawable.home_mit_buildkite
        "capacitor.config.json" -> R.drawable.home_mit_capacitor
        "changes.rst" -> R.drawable.home_mit_changelog
        "codecov.yaml" -> R.drawable.home_mit_codecov
        "commitlintrc.jsonc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_commitlint else 0
        "compose.test.yml",
        "containerfile.prod",
        "dockerfile.ci" -> R.drawable.home_mit_docker
        "copying.md" -> R.drawable.home_mit_license
        "cracorc.js" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_craco else 0
        "dune" -> R.drawable.home_mit_dune
        "ecosystem.config.mjs" -> R.drawable.home_mit_pm2_ecosystem
        "esbuild.config.mjs",
        "esbuild.mjs",
        "esbuild.prod.ts",
        "esbuild.stage.mjs",
        "esbuild.test.mjs" -> R.drawable.home_mit_esbuild
        "excalidraw.svg" -> R.drawable.home_mit_excalidraw
        "fastfile" -> R.drawable.home_mit_fastlane
        "gatsby-config.js",
        "gatsby-node.js" -> R.drawable.home_mit_gatsby
        "gnumakefile" -> R.drawable.home_mit_makefile
        "graphqlrc.cjs",
        "graphqlrc.mts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_graphql else 0
        "huskyrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_husky else 0
        "ionic.config.json" -> R.drawable.home_mit_ionic
        "jest-preset.mjs",
        "jest.config.cts",
        "jest.e2e.config.cjs",
        "jest.e2e.config.mts" -> R.drawable.home_mit_jest
        "justfile" -> R.drawable.home_mit_just
        "karma.conf.coffee" -> R.drawable.home_mit_karma
        "kcl.mod" -> R.drawable.home_mit_kcl
        "markdoc.config.ts" -> R.drawable.home_mit_markdoc_config
        "nano-staged.js" -> if (light) R.drawable.home_mit_nano_staged_light else R.drawable.home_mit_nano_staged
        "netlify.yaml" -> if (light) R.drawable.home_mit_netlify_light else R.drawable.home_mit_netlify
        "nginx.conf" -> R.drawable.home_mit_nginx
        "pklproject" -> R.drawable.home_mit_pkl
        "pnpm-lock.yaml" -> if (light) R.drawable.home_mit_pnpm_light else R.drawable.home_mit_pnpm
        "postcss.config.yaml" -> R.drawable.home_mit_postcss
        "postcssrc.cts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_postcss else 0
        "prettier.config.yaml" -> R.drawable.home_mit_prettier
        "prettierrc.cts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_prettier else 0
        "prisma.config.mjs" -> R.drawable.home_mit_prisma
        "puppeteerrc.js" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_puppeteer else 0
        "pyproject.toml" -> R.drawable.home_mit_python_misc
        "releaserc" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release else 0
        "rollup.config.dev.js" -> R.drawable.home_mit_rollup
        "rstack.config.cjs",
        "rstack.config.mts" -> R.drawable.home_mit_rstack
        "sentry.edge.config.cts" -> R.drawable.home_mit_sentry
        "snowpack.config.cts" -> if (light) R.drawable.home_mit_snowpack_light else R.drawable.home_mit_snowpack
        "stylelintrc.ts" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint else 0
        "svelte.config.ts" -> R.drawable.home_mit_svelte
        "svgrrc.js" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_svgr else 0
        "syncpackrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_syncpack else 0
        "tauri.macos.conf.json",
        "tauri.windows.conf.json" -> R.drawable.home_mit_tauri
        "todos.md" -> R.drawable.home_mit_todo
        "tsconfig.base.json",
        "tsconfig.eslint.json" -> R.drawable.home_mit_tsconfig
        "tsdown.config.js" -> R.drawable.home_mit_tsdown
        "turbo.json" -> if (light) R.drawable.home_mit_turborepo_light else R.drawable.home_mit_turborepo
        "typedoc.json" -> R.drawable.home_mit_typedoc
        "unlicense" -> R.drawable.home_mit_unlicense
        "uno.config.mts" -> R.drawable.home_mit_unocss
        "uv.lock" -> R.drawable.home_mit_uv
        "vue.config.cjs" -> R.drawable.home_mit_vue_config
        "webpack.config.babel.js",
        "webpack.config.dev.cjs",
        "webpack.config.dev.mts",
        "webpack.config.main.cts",
        "webpack.config.production.babel.mjs",
        "webpack.config.production.cts",
        "webpack.config.staging.mjs",
        "webpack.config.vendor.production.cts",
        "webpack.dev.cts",
        "webpack.development.cjs",
        "webpack.development.mts",
        "webpack.production.cjs",
        "webpack.production.mts",
        "webpack.server.cts" -> R.drawable.home_mit_webpack
        "windi.config.js" -> R.drawable.home_mit_windicss
        "zeabur.json5" -> if (light) R.drawable.home_mit_zeabur_light else R.drawable.home_mit_zeabur
        else -> 0
    }

    private fun named9(key: String, path: String, light: Boolean): Int = when (key) {
        ".babel-plugin-macrosrc.mjs",
        ".babelrc.json" -> R.drawable.home_mit_babel
        ".codecov.yml" -> R.drawable.home_mit_codecov
        ".commitlintrc" -> R.drawable.home_mit_commitlint
        ".cracorc.mjs" -> R.drawable.home_mit_craco
        ".env.development.local",
        ".env.e2e",
        ".env.preview",
        ".env.sample",
        ".env.staging.local",
        ".env.template",
        ".env.uat" -> R.drawable.home_mit_tune
        ".git-blame-ignore",
        ".gitkeep",
        "git-history" -> R.drawable.home_mit_git
        ".graphqlrc.jsonc",
        ".graphqlrc.yaml" -> R.drawable.home_mit_graphql
        ".hushlogin" -> R.drawable.home_mit_console
        ".lefthookrc" -> R.drawable.home_mit_lefthook
        ".mocharc.json" -> R.drawable.home_mit_mocha
        ".nano-staged.mjs",
        ".nanostagedrc" -> if (light) R.drawable.home_mit_nano_staged_light else R.drawable.home_mit_nano_staged
        ".packshiprc.ts",
        "packship.config.mts" -> R.drawable.home_mit_packship
        ".php_cs.dist.php" -> R.drawable.home_mit_php_cs_fixer
        ".posthtmlrc.json5",
        "posthtml.config.cts" -> R.drawable.home_mit_posthtml
        ".pug-lintrc.json" -> R.drawable.home_mit_pug
        ".puppeteerrc.mjs" -> R.drawable.home_mit_puppeteer
        ".release-it.toml",
        ".release-it.yml" -> R.drawable.home_mit_rocket
        ".releaserc.json5" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        ".remarkrc.yml" -> R.drawable.home_mit_remark
        ".rubocop_todo.yml" -> if (light) R.drawable.home_mit_rubocop_light else R.drawable.home_mit_rubocop
        ".stryker.config.js" -> R.drawable.home_mit_stryker
        ".svgrrc.json" -> R.drawable.home_mit_svgr
        ".textlintrc.yml" -> R.drawable.home_mit_textlint
        ".umirc.ts" -> R.drawable.home_mit_umi
        ".uv.toml" -> R.drawable.home_mit_uv
        ".vuerc",
        "vetur.config.js" -> R.drawable.home_mit_vue_config
        "appwrite.js" -> R.drawable.home_mit_appwrite
        "astro.config.mjs" -> R.drawable.home_mit_astro_config
        "aurelia.json" -> R.drawable.home_mit_aurelia
        "babel-plugin-macrosrc.toml",
        "babel-plugin-macrosrc.yml",
        "babelrc.cjs",
        "babelrc.mts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "bashly-settings.yml" -> if (light) R.drawable.home_mit_bashly_settings_light else R.drawable.home_mit_bashly_settings
        "browserslist" -> if (light) R.drawable.home_mit_browserlist_light else R.drawable.home_mit_browserlist
        "changelog.rst" -> R.drawable.home_mit_changelog
        "commitlintrc.cjs",
        "commitlintrc.mts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_commitlint else 0
        "compose.beta.yaml",
        "compose.development.yml",
        "compose.local.yml",
        "compose.staging.yml",
        "docker-compose.ci.yml",
        "docker-compose.dev.yaml",
        "dockerfile.development",
        "dockerfile.local",
        "dockerfile.prod",
        "dockerfile.staging" -> R.drawable.home_mit_docker
        "composer.lock" -> R.drawable.home_mit_json
        "copyright.txt" -> R.drawable.home_mit_license
        "cracorc.toml",
        "cracorc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_craco else 0
        "cypress.json" -> R.drawable.home_mit_cypress
        "esbuild.prod.cts" -> R.drawable.home_mit_esbuild
        "eslint.config.toml",
        "eslint.config.yml" -> R.drawable.home_mit_eslint
        "firestore.indexes.json" -> R.drawable.home_mit_firebase
        "graphqlrc.json5" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_graphql else 0
        "gulpfile.js" -> R.drawable.home_mit_gulp
        "harmonix.config.js" -> R.drawable.home_mit_harmonix
        "histoire.config.mjs" -> R.drawable.home_mit_histoire
        "husky.config.jsonc",
        "husky.config.yaml" -> R.drawable.home_mit_husky
        "huskyrc.cts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_husky else 0
        "jest.config.ts",
        "jest.preset.mjs" -> R.drawable.home_mit_jest
        "lighthouserc.cjs" -> R.drawable.home_mit_lighthouse
        "markdoc.config.cts" -> R.drawable.home_mit_markdoc_config
        "netlify.json" -> if (light) R.drawable.home_mit_netlify_light else R.drawable.home_mit_netlify
        "oxlint.config.ts" -> R.drawable.home_mit_oxc
        "playwright-ct.config.cjs",
        "playwright-ct.config.mts" -> R.drawable.home_mit_playwright
        "postcss.config.json" -> R.drawable.home_mit_postcss
        "postcssrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_postcss else 0
        "posthtmlrc.jsonc",
        "posthtmlrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_posthtml else 0
        "prettier.config.json" -> R.drawable.home_mit_prettier
        "prettierrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_prettier else 0
        "puppeteerrc.toml",
        "puppeteerrc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_puppeteer else 0
        "roadmap.md" -> R.drawable.home_mit_roadmap
        "rolldown.config.mjs" -> R.drawable.home_mit_rolldown
        "rollup.config.common.mjs" -> R.drawable.home_mit_rollup
        "rspress.config.js" -> R.drawable.home_mit_rstack
        "scanfile" -> R.drawable.home_mit_ruby
        "screwdriver.yaml" -> R.drawable.home_mit_screwdriver
        "sentry.edge.config.ts",
        "sentry.server.config.js" -> R.drawable.home_mit_sentry
        "serverless.yaml" -> R.drawable.home_mit_serverless
        "shellcheckrc" -> if (light) R.drawable.home_mit_shellcheck_light else R.drawable.home_mit_shellcheck
        "snowpack.config.ts" -> if (light) R.drawable.home_mit_snowpack_light else R.drawable.home_mit_snowpack
        "sonarqube.analysis.xml" -> R.drawable.home_mit_sonarcloud
        "stencil.config.ts" -> R.drawable.home_mit_stencil
        "stylelint.config.jsonc",
        "stylelint.config.yaml" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        "stylelintrc.cts" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint else 0
        "supabase.ts" -> R.drawable.home_mit_supabase
        "svelte.config.cts" -> R.drawable.home_mit_svelte
        "svgrrc.toml",
        "svgrrc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_svgr else 0
        "syncpack.config.jsonc",
        "syncpack.config.yaml" -> R.drawable.home_mit_syncpack
        "syncpackrc.cts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_syncpack else 0
        "tags" -> R.drawable.home_mit_label
        "tauri.linux.conf.json" -> R.drawable.home_mit_tauri
        "tiltfile" -> R.drawable.home_mit_tilt
        "tsconfig.declaration.json",
        "tsconfig.lib.prod.json" -> R.drawable.home_mit_tsconfig
        "typedoc.js" -> R.drawable.home_mit_typedoc
        "vitest.config.cjs",
        "vitest.config.mts",
        "vitest.e2e.config.mjs" -> R.drawable.home_mit_vitest
        "wallaby.js" -> R.drawable.home_mit_wallaby
        "wally.toml" -> R.drawable.home_mit_wally
        "webpack.config.main.ts",
        "webpack.config.prod.babel.js",
        "webpack.config.production.ts",
        "webpack.config.renderer.js",
        "webpack.config.server.js",
        "webpack.config.vendor.production.ts",
        "webpack.dev.ts",
        "webpack.mix.cjs",
        "webpack.mix.mts",
        "webpack.server.ts" -> R.drawable.home_mit_webpack
        else -> 0
    }

    private fun named10(key: String, path: String, light: Boolean): Int = when (key) {
        ".babelrc.js",
        ".babelrc.jsonc",
        "babel-plugin-macros.config.mjs",
        "babel.config.cts" -> R.drawable.home_mit_babel
        ".bowerrc" -> R.drawable.home_mit_bower
        ".eslintrc.yml" -> R.drawable.home_mit_eslint
        ".gitignore-global",
        "merge_msg" -> R.drawable.home_mit_git
        ".graphqlrc.cjs",
        ".graphqlrc.json",
        ".graphqlrc.mts" -> R.drawable.home_mit_graphql
        ".histoire.js" -> R.drawable.home_mit_histoire
        ".istanbul.yml",
        ".nycrc.yaml" -> R.drawable.home_mit_istanbul
        ".markdownlint.yml" -> R.drawable.home_mit_markdownlint
        ".mocharc.js",
        ".mocharc.jsonc" -> R.drawable.home_mit_mocha
        ".nowignore" -> if (light) R.drawable.home_mit_vercel_light else R.drawable.home_mit_vercel
        ".nxignore" -> R.drawable.home_mit_nx
        ".postcssrc.cts",
        "postcss.config.js",
        "postcss.config.jsonc" -> R.drawable.home_mit_postcss
        ".posthtmlrc" -> R.drawable.home_mit_posthtml
        ".prettierrc.mjs",
        "prettier.config.js",
        "prettier.config.jsonc" -> R.drawable.home_mit_prettier
        ".pug-lintrc.js" -> R.drawable.home_mit_pug
        ".releaserc" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        ".sequelizerc" -> R.drawable.home_mit_sequelize
        ".svgrrc.js",
        ".svgrrc.jsonc",
        "svgr.config.mjs" -> R.drawable.home_mit_svgr
        ".syncpackrc.ts",
        "syncpack.config.cjs",
        "syncpack.config.json",
        "syncpack.config.mts" -> R.drawable.home_mit_syncpack
        ".taurignore" -> R.drawable.home_mit_tauri
        ".vars" -> R.drawable.home_mit_tune
        "artisan" -> R.drawable.home_mit_laravel
        "authors.txt",
        "contributors.txt" -> R.drawable.home_mit_authors
        "auto-config.yml" -> if (light) R.drawable.home_mit_auto_light else R.drawable.home_mit_auto
        "babelrc.json5",
        "babelrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "bitbucket-pipelines.yaml" -> R.drawable.home_mit_bitbucket
        "cabal.project" -> R.drawable.home_mit_cabal
        "circle.yml" -> if (light) R.drawable.home_mit_circleci_light else R.drawable.home_mit_circleci
        "claude.local.md" -> R.drawable.home_mit_claude
        "commitlint.config.cts" -> R.drawable.home_mit_commitlint
        "commitlintrc.json5",
        "commitlintrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_commitlint else 0
        "containerfile.development",
        "containerfile.local",
        "containerfile.production",
        "containerfile.staging",
        "docker-compose.prod.yml",
        "docker-compose.test.yaml",
        "docker-compose.web.yaml" -> R.drawable.home_mit_docker
        "contributing" -> R.drawable.home_mit_contributing
        "craco.config.mjs" -> R.drawable.home_mit_craco
        "credits" -> R.drawable.home_mit_credits
        "cypress.config.ts",
        "cypress.env.json" -> R.drawable.home_mit_cypress
        "devcontainer-lock.json",
        "devcontainer.json" -> if (hasIconParent(path, ".devcontainer")) R.drawable.home_mit_container else 0
        "drizzle.config.dev.ts",
        "drizzle.config.json" -> R.drawable.home_mit_drizzle
        "eslintrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_eslint else 0
        "gradle.properties" -> R.drawable.home_mit_gradle
        "graphqlrc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_graphql else 0
        "gruntfile.cjs" -> R.drawable.home_mit_grunt
        "husky.config.cjs",
        "husky.config.json",
        "husky.config.mts" -> R.drawable.home_mit_husky
        "installation" -> R.drawable.home_mit_installation
        "jest.json" -> R.drawable.home_mit_jest
        "keystatic.config.ts" -> R.drawable.home_mit_keystatic
        "lerna.json" -> R.drawable.home_mit_lerna
        "licence-apache",
        "license-apache" -> R.drawable.home_mit_license
        "lighthouserc.yaml" -> R.drawable.home_mit_lighthouse
        "makefile" -> R.drawable.home_mit_makefile
        "meson.build",
        "meson.options" -> R.drawable.home_mit_meson
        "next.config.js" -> if (light) R.drawable.home_mit_next_light else R.drawable.home_mit_next
        "nuxt.config.js" -> R.drawable.home_mit_nuxt
        "openapi.yml" -> if (light) R.drawable.home_mit_openapi_light else R.drawable.home_mit_openapi
        "panda.config.cts" -> R.drawable.home_mit_panda
        "payload.config.mjs" -> if (light) R.drawable.home_mit_payload_light else R.drawable.home_mit_payload
        "posthtmlrc.cjs",
        "posthtmlrc.json",
        "posthtmlrc.mts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_posthtml else 0
        "prisma.yml" -> R.drawable.home_mit_prisma
        "puppeteer.config.mjs" -> R.drawable.home_mit_puppeteer
        "rollup.config.prod.js" -> R.drawable.home_mit_rollup
        "route.jsx",
        "router.jsx" -> R.drawable.home_mit_routing
        "rsbuild.config.ts",
        "rslib.config.cjs",
        "rslib.config.mts",
        "rslint.config.cjs",
        "rslint.config.mts",
        "rspack.config.cts",
        "rstest.config.js" -> R.drawable.home_mit_rstack
        "sconscript" -> if (light) R.drawable.home_mit_scons_light else R.drawable.home_mit_scons
        "sentry.client.config.cts" -> R.drawable.home_mit_sentry
        "serverless.json" -> R.drawable.home_mit_serverless
        "steadybit.yaml" -> R.drawable.home_mit_steadybit
        "stryker.conf.mjs" -> R.drawable.home_mit_stryker
        "stylelint.config.cjs",
        "stylelint.config.json",
        "stylelint.config.mts" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        "unlicense.txt" -> R.drawable.home_mit_unlicense
        "unocss.config.ts" -> R.drawable.home_mit_unocss
        "v.mod" -> R.drawable.home_mit_vlang
        "velite.config.ts" -> R.drawable.home_mit_velite
        "vite.config.cjs",
        "vite.config.mts" -> R.drawable.home_mit_vite
        "vitest.unit.config.ts",
        "vitest.workspace.cjs",
        "vitest.workspace.mts" -> R.drawable.home_mit_vitest
        "webpack.base.mjs",
        "webpack.client.cjs",
        "webpack.client.mts",
        "webpack.common.cjs",
        "webpack.common.mts",
        "webpack.config.base.cjs",
        "webpack.config.base.mts",
        "webpack.config.client.cts",
        "webpack.config.common.babel.mjs",
        "webpack.config.common.cts",
        "webpack.config.dev.babel.cts",
        "webpack.config.prod.mjs",
        "webpack.config.staging.babel.cjs",
        "webpack.config.staging.babel.mts",
        "webpack.config.test.js",
        "webpack.config.ts",
        "webpack.config.vendor.ts",
        "webpack.js",
        "webpack.test.ts" -> R.drawable.home_mit_webpack
        "wxt.config.js" -> R.drawable.home_mit_wxt
        else -> 0
    }

    private fun named11(key: String, path: String, light: Boolean): Int = when (key) {
        ".adonisrc.json" -> R.drawable.home_mit_adonis
        ".babel-plugin-macrosrc.yml",
        ".babelrc.cjs",
        ".babelrc.mts",
        ".babelrc.toml",
        "babel-plugin-macros.config.ts" -> R.drawable.home_mit_babel
        ".buckconfig" -> R.drawable.home_mit_buck
        ".cracorc.yml",
        "craco.config.ts" -> R.drawable.home_mit_craco
        ".easignore" -> if (light) R.drawable.home_mit_expo_light else R.drawable.home_mit_expo
        ".ember-cli.js" -> R.drawable.home_mit_ember
        ".env.example",
        ".env.preprod" -> R.drawable.home_mit_tune
        ".gitattributes" -> R.drawable.home_mit_git
        ".gitpod.yml" -> R.drawable.home_mit_gitpod
        ".graphqlrc.js",
        ".graphqlrc.json5",
        "graphql.config.cts" -> R.drawable.home_mit_graphql
        ".hgflow" -> R.drawable.home_mit_mercurial
        ".histoire.cjs",
        ".histoire.mts" -> R.drawable.home_mit_histoire
        ".huskyrc.cts",
        "husky.config.js",
        "husky.config.json5" -> R.drawable.home_mit_husky
        ".io-config.json" -> R.drawable.home_mit_ionic
        ".jestrc",
        ".jestrc.json" -> R.drawable.home_mit_jest
        ".mcattributes" -> R.drawable.home_mit_minecraft
        ".mocharc.cjs" -> R.drawable.home_mit_mocha
        ".node-version" -> R.drawable.home_mit_nodejs
        ".nycrc",
        ".nycrc.json" -> R.drawable.home_mit_istanbul
        ".oxlintrc.json" -> R.drawable.home_mit_oxc
        ".posthtmlrc.yaml" -> R.drawable.home_mit_posthtml
        ".prettierrc.ts",
        "prettier.config.cjs",
        "prettier.config.mts",
        "prettier.config.toml" -> R.drawable.home_mit_prettier
        ".puppeteerrc.yml",
        "puppeteer.config.ts" -> R.drawable.home_mit_puppeteer
        ".pylintrc" -> R.drawable.home_mit_python_misc
        ".releaserc.yaml" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        ".slugignore" -> R.drawable.home_mit_slug
        ".snyk" -> R.drawable.home_mit_snyk
        ".stylelintrc.cts",
        "stylelint.config.js",
        "stylelint.config.json5" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        ".svgrrc.cjs",
        ".svgrrc.mts",
        ".svgrrc.toml",
        "svgr.config.ts" -> R.drawable.home_mit_svgr
        ".syncpackrc.mjs",
        "syncpack.config.js",
        "syncpack.config.json5" -> R.drawable.home_mit_syncpack
        ".watchmanconfig" -> R.drawable.home_mit_watchman
        "babelrc",
        "babelrc.json" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "bashly.yml" -> if (hasIconParent(path, "src")) R.drawable.home_mit_bashly else 0
        "bashrc_apple_terminal",
        "commit-msg",
        "post-merge" -> R.drawable.home_mit_console
        "cabal.project.freeze" -> R.drawable.home_mit_cabal
        "commitlintrc",
        "commitlintrc.json" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_commitlint else 0
        "compose.worker.yaml",
        "dockerfile.production" -> R.drawable.home_mit_docker
        "contributing.md" -> R.drawable.home_mit_contributing
        "credits.md" -> R.drawable.home_mit_credits
        "cypress.config.mjs" -> R.drawable.home_mit_cypress
        "drizzle.config.js" -> R.drawable.home_mit_drizzle
        "duc.fbs" -> R.drawable.home_mit_duc
        "esbuild.dev.cts" -> R.drawable.home_mit_esbuild
        "eslintrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_eslint else 0
        "favicon.ico" -> R.drawable.home_mit_favicon
        "gemfile" -> R.drawable.home_mit_gemfile
        "graphqlrc.yaml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_graphql else 0
        "gruntfile.js" -> R.drawable.home_mit_grunt
        "hosts" -> if (light) R.drawable.home_mit_hosts_light else R.drawable.home_mit_hosts
        "install" -> R.drawable.home_mit_installation
        "jsconfig.json" -> R.drawable.home_mit_jsconfig
        "karma.conf.ts" -> R.drawable.home_mit_karma
        "labeler.yml" -> if (hasIconParent(path, ".github")) R.drawable.home_mit_label else 0
        "lefthook.yaml",
        "lefthookrc" -> R.drawable.home_mit_lefthook
        "lighthouserc.json" -> R.drawable.home_mit_lighthouse
        "meson_options.txt" -> R.drawable.home_mit_meson
        "metro.config.cts" -> R.drawable.home_mit_metro
        "moon.yml" -> R.drawable.home_mit_moon
        "nest-cli.json" -> R.drawable.home_mit_nest
        "netlify.toml" -> if (light) R.drawable.home_mit_netlify_light else R.drawable.home_mit_netlify
        "next.config.mts" -> if (light) R.drawable.home_mit_next_light else R.drawable.home_mit_next
        "packship.config.json" -> R.drawable.home_mit_packship
        "payload.config.ts" -> if (light) R.drawable.home_mit_payload_light else R.drawable.home_mit_payload
        "playwright.config.cts" -> R.drawable.home_mit_playwright
        "postcss.config.cjs",
        "postcss.config.mts",
        "postcss.config.toml" -> R.drawable.home_mit_postcss
        "posthtmlrc.js",
        "posthtmlrc.json5" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_posthtml else 0
        "project.garden.yaml" -> R.drawable.home_mit_garden
        "robots.txt" -> R.drawable.home_mit_robots
        "routes.jsx" -> R.drawable.home_mit_routing
        "rsbuild.config.mjs",
        "rslib.config.js",
        "rslint.config.js",
        "rstest.config.cjs",
        "rstest.config.mts" -> R.drawable.home_mit_rstack
        "semgrep.yml" -> R.drawable.home_mit_semgrep
        "serverless.js" -> R.drawable.home_mit_serverless
        "swagger.yaml" -> R.drawable.home_mit_swagger
        "taskfile.yml" -> R.drawable.home_mit_taskfile
        "tsconfig.doc.json",
        "tsconfig.e2e.json",
        "tsconfig.worker.json" -> R.drawable.home_mit_tsconfig
        "turbo.jsonc" -> if (light) R.drawable.home_mit_turborepo_light else R.drawable.home_mit_turborepo
        "unocss.config.mjs" -> R.drawable.home_mit_unocss
        "velite.config.mjs" -> R.drawable.home_mit_velite
        "vite.config.js" -> R.drawable.home_mit_vite
        "vitest.unit.config.mjs",
        "vitest.workspace.js" -> R.drawable.home_mit_vitest
        "volar.config.js" -> R.drawable.home_mit_vue_config
        "webpack.base.ts",
        "webpack.cjs",
        "webpack.client.js",
        "webpack.common.js",
        "webpack.config.base.js",
        "webpack.config.common.babel.ts",
        "webpack.config.mjs",
        "webpack.config.prod.ts",
        "webpack.config.staging.babel.js",
        "webpack.config.test.cjs",
        "webpack.config.test.mts",
        "webpack.config.vendor.mjs",
        "webpack.dist.cts",
        "webpack.mts",
        "webpack.test.mjs" -> R.drawable.home_mit_webpack
        "wxt.config.cjs",
        "wxt.config.mts" -> R.drawable.home_mit_wxt
        else -> 0
    }

    private fun named12(key: String, path: String, light: Boolean): Int = when (key) {
        ".babel-plugin-macrosrc.ts",
        ".babelrc.json5",
        "babel-plugin-macros.config.yml" -> R.drawable.home_mit_babel
        ".commitlintrc.yaml" -> R.drawable.home_mit_commitlint
        ".cracorc.ts",
        "craco.config.yml" -> R.drawable.home_mit_craco
        ".ember-cli" -> R.drawable.home_mit_ember
        ".env.development",
        ".env.local",
        ".env.qa.local",
        ".env.staging" -> R.drawable.home_mit_tune
        ".gitignore_global" -> R.drawable.home_mit_git
        ".graphqlrc",
        ".graphqlrc.toml" -> R.drawable.home_mit_graphql
        ".jestrc.js",
        "jest-unit.config.js",
        "jest.config.mjs",
        "jest.e2e.config.json",
        "jest.setup.js" -> R.drawable.home_mit_jest
        ".lighthouserc.yaml",
        "lighthouserc.js" -> R.drawable.home_mit_lighthouse
        ".luaurc" -> R.drawable.home_mit_luau
        ".markdownlint-cli2.jsonc" -> R.drawable.home_mit_markdownlint
        ".mcdefinitions" -> R.drawable.home_mit_minecraft
        ".mjmlconfig" -> R.drawable.home_mit_mjml
        ".posthtmlrc.json" -> R.drawable.home_mit_posthtml
        ".prettierrc.yml",
        "prettier.config.json5" -> R.drawable.home_mit_prettier
        ".puppeteerrc.ts",
        "puppeteer.config.yml" -> R.drawable.home_mit_puppeteer
        ".releaserc.json",
        "release.config.yaml" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        ".stackblitzrc" -> R.drawable.home_mit_stackblitz
        ".stryker.config.cjs" -> R.drawable.home_mit_stryker
        ".stylelintcache",
        "stylelint.config.toml" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        ".svgrrc.json5",
        "svgr.config.yml" -> R.drawable.home_mit_svgr
        ".swiftformat" -> R.drawable.home_mit_swift
        ".umirc.mjs" -> R.drawable.home_mit_umi
        "astro.config.ts" -> R.drawable.home_mit_astro_config
        "auto.config.ts" -> if (light) R.drawable.home_mit_auto_light else R.drawable.home_mit_auto
        "babel-plugin-macrosrc.jsonc",
        "babelrc.js" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "blitz.config.js" -> R.drawable.home_mit_blitz
        "cdp.pid" -> R.drawable.home_mit_json
        "celerybeat-schedule" -> R.drawable.home_mit_python_misc
        "citation.cff" -> R.drawable.home_mit_citation
        "commitlintrc.js" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_commitlint else 0
        "compose.alpha.yml",
        "compose.ci.yml",
        "compose.dev.yaml",
        "compose.stage.yml",
        "compose.yml",
        "docker-compose.beta.yaml",
        "docker-compose.development.yml",
        "docker-compose.local.yml",
        "docker-compose.production.yml",
        "docker-compose.staging.yml",
        "dockerfile",
        "dockerfile.alpha",
        "dockerfile.stage" -> R.drawable.home_mit_docker
        "contentlayer.config.cts" -> R.drawable.home_mit_contentlayer
        "contributing.txt" -> R.drawable.home_mit_contributing
        "copyright.rst",
        "licence-mit",
        "license-mit" -> R.drawable.home_mit_license
        "cracorc.jsonc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_craco else 0
        "credits.txt" -> R.drawable.home_mit_credits
        "dependabot.yml" -> R.drawable.home_mit_dependabot
        "eslint.config.jsonc" -> R.drawable.home_mit_eslint
        "eslintrc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_eslint else 0
        "gatsby-browser.js" -> R.drawable.home_mit_gatsby
        "gleam.toml" -> R.drawable.home_mit_gleam
        "go.mod" -> R.drawable.home_mit_go_mod
        "graphqlrc.json" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_graphql else 0
        "gulpfile.cjs",
        "gulpfile.mts" -> R.drawable.home_mit_gulp
        "histoire.config.ts" -> R.drawable.home_mit_histoire
        "husky.config.toml" -> R.drawable.home_mit_husky
        "knip.json" -> R.drawable.home_mit_knip
        "lefthook.json" -> R.drawable.home_mit_lefthook
        "nestconfig.json" -> R.drawable.home_mit_nest
        "nodemon.json" -> R.drawable.home_mit_nodemon
        "nx.json" -> R.drawable.home_mit_nx
        "packship.config.js" -> R.drawable.home_mit_packship
        "pklproject.deps.json" -> R.drawable.home_mit_pkl
        "plastic.workspaces" -> R.drawable.home_mit_plastic
        "playwright-ct.config.js",
        "playwright.config.base.cts" -> R.drawable.home_mit_playwright
        "postcss.config.json5" -> R.drawable.home_mit_postcss
        "postcssrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_postcss else 0
        "posthtmlrc",
        "posthtmlrc.toml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_posthtml else 0
        "prettierrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_prettier else 0
        "puppeteerrc.jsonc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_puppeteer else 0
        "quasar.conf.js" -> R.drawable.home_mit_quasar
        "releaserc.cts" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release else 0
        "rolldown.config.ts" -> R.drawable.home_mit_rolldown
        "rollup.config.common.ts" -> R.drawable.home_mit_rollup
        "rspress.config.cjs",
        "rspress.config.mts" -> R.drawable.home_mit_rstack
        "sentry.edge.config.mjs",
        "sentry.server.config.cjs",
        "sentry.server.config.mts" -> R.drawable.home_mit_sentry
        "snakefile" -> R.drawable.home_mit_snakemake
        "snowpack.config.mjs" -> if (light) R.drawable.home_mit_snowpack_light else R.drawable.home_mit_snowpack
        "stitches.config.ts" -> if (light) R.drawable.home_mit_stitches_light else R.drawable.home_mit_stitches
        "svgrrc.jsonc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_svgr else 0
        "swagger.json" -> R.drawable.home_mit_swagger
        "syncpack.config.toml" -> R.drawable.home_mit_syncpack
        "tailwind.config.cts" -> R.drawable.home_mit_tailwindcss
        "taze.config.cts" -> R.drawable.home_mit_taze
        "trigger.config.cts" -> R.drawable.home_mit_trigger
        "tsconfig.editor.json" -> R.drawable.home_mit_tsconfig
        "typst.toml" -> R.drawable.home_mit_typst
        "vitest.config.js",
        "vitest.e2e.config.ts" -> R.drawable.home_mit_vitest
        "webpack.config.base.babel.cts",
        "webpack.config.main.mjs",
        "webpack.config.prod.babel.cjs",
        "webpack.config.prod.babel.mts",
        "webpack.config.production.mjs",
        "webpack.config.renderer.cjs",
        "webpack.config.renderer.mts",
        "webpack.config.server.cjs",
        "webpack.config.server.mts",
        "webpack.config.vendor.production.mjs",
        "webpack.dev.mjs",
        "webpack.mix.js",
        "webpack.prod.config.cts",
        "webpack.prod.cts",
        "webpack.server.mjs" -> R.drawable.home_mit_webpack
        "werf.yml" -> R.drawable.home_mit_werf
        "wrangler.json" -> R.drawable.home_mit_wrangler
        "zeabur.yaml" -> if (light) R.drawable.home_mit_zeabur_light else R.drawable.home_mit_zeabur
        else -> 0
    }

    private fun named13(key: String, path: String, light: Boolean): Int = when (key) {
        ".babelrc" -> R.drawable.home_mit_babel
        ".commitlint.yaml",
        ".commitlintrc.cts",
        ".commitlintrc.json" -> R.drawable.home_mit_commitlint
        ".ecrc",
        ".editorconfig" -> R.drawable.home_mit_editorconfig
        ".eslintrc.jsonc",
        ".eslintrc.ts",
        "eslint.config.cjs",
        "eslint.config.mts" -> R.drawable.home_mit_eslint
        ".gdignore" -> R.drawable.home_mit_godot_assets
        ".graphqlconfig" -> R.drawable.home_mit_graphql
        ".hg" -> R.drawable.home_mit_mercurial
        ".k8s.yaml" -> R.drawable.home_mit_kubernetes
        ".lefthook.yaml",
        "lefthook-local.yaml" -> R.drawable.home_mit_lefthook
        ".lighthouserc.json" -> R.drawable.home_mit_lighthouse
        ".lintstagedrc.yaml",
        "lint-staged.config.ts" -> R.drawable.home_mit_lintstaged
        ".markdownlint-cli2.cjs",
        ".markdownlint.jsonc" -> R.drawable.home_mit_markdownlint
        ".nest-cli.json" -> R.drawable.home_mit_nest
        ".posthtmlrc.js",
        "posthtml.config.mjs" -> R.drawable.home_mit_posthtml
        ".pug-lintrc" -> R.drawable.home_mit_pug
        ".release-it.cjs" -> R.drawable.home_mit_rocket
        ".releaserc.js",
        "release.config.cts",
        "release.config.json" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        ".remarkrc.cjs" -> R.drawable.home_mit_remark
        ".svgrrc" -> R.drawable.home_mit_svgr
        ".syncpackrc.yml" -> R.drawable.home_mit_syncpack
        ".textlintrc.cjs" -> R.drawable.home_mit_textlint
        ".vsconfig" -> R.drawable.home_mit_visualstudio
        ".yarnrc.yml" -> R.drawable.home_mit_yarn
        "appfile" -> R.drawable.home_mit_fastlane
        "authors.md",
        "authors.rst",
        "contributors.md",
        "contributors.rst" -> R.drawable.home_mit_authors
        "auto-config.ts" -> if (light) R.drawable.home_mit_auto_light else R.drawable.home_mit_auto
        "azure-pipelines-main.yml",
        "azure-pipelines.yml" -> R.drawable.home_mit_azure_pipelines
        "babel-plugin-macrosrc.cjs",
        "babel-plugin-macrosrc.mts",
        "babelrc.toml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "biome.jsonc" -> R.drawable.home_mit_biome
        "codeowners" -> R.drawable.home_mit_codeowners
        "commitlintrc.toml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_commitlint else 0
        "compile_flags.txt" -> R.drawable.home_mit_settings
        "compose.override.yaml",
        "compose.prod.yml",
        "compose.web.yaml",
        "containerfile",
        "containerfile.alpha",
        "containerfile.stage" -> R.drawable.home_mit_docker
        "copying" -> R.drawable.home_mit_license
        "cracorc.cjs",
        "cracorc.mts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_craco else 0
        "drizzle.config.prod.json" -> R.drawable.home_mit_drizzle
        "ecosystem.config.ts" -> R.drawable.home_mit_pm2_ecosystem
        "esbuild.config.ts",
        "esbuild.prod.mjs",
        "esbuild.stage.ts",
        "esbuild.test.ts",
        "esbuild.ts" -> R.drawable.home_mit_esbuild
        "excalidraw.png" -> R.drawable.home_mit_excalidraw
        "fabric.mod.json" -> R.drawable.home_mit_minecraft_fabric
        "graphqlrc.js" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_graphql else 0
        "gulpfile.babel.js" -> R.drawable.home_mit_gulp
        "hardhat.config.ts" -> R.drawable.home_mit_hardhat
        "huskyrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_husky else 0
        "jakefile" -> R.drawable.home_mit_javascript
        "jest-e2e.config.cts",
        "jest-e2e.config.json",
        "jest-github-actions-reporter.js",
        "jest.e2e.config.js" -> R.drawable.home_mit_jest
        "jsr.json" -> if (light) R.drawable.home_mit_jsr_light else R.drawable.home_mit_jsr
        "karma.config.ts" -> R.drawable.home_mit_karma
        "knip.js" -> R.drawable.home_mit_knip
        "lynx.config.cts" -> R.drawable.home_mit_lynx
        "markdoc.config.mjs" -> R.drawable.home_mit_markdoc_config
        "nano-staged.cjs" -> if (light) R.drawable.home_mit_nano_staged_light else R.drawable.home_mit_nano_staged
        "plastic.selector" -> R.drawable.home_mit_plastic
        "prisma.config.ts",
        "prisma7.config.cts" -> R.drawable.home_mit_prisma
        "puppeteerrc.cjs",
        "puppeteerrc.mts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_puppeteer else 0
        "pylintrc" -> R.drawable.home_mit_python_misc
        "releaserc.yaml" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release else 0
        "routes.js" -> R.drawable.home_mit_routing
        "rstack.config.js" -> R.drawable.home_mit_rstack
        "stylelintrc.mjs" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint else 0
        "svelte.config.mjs" -> R.drawable.home_mit_svelte
        "svgo.config.mjs" -> R.drawable.home_mit_svgo
        "svgrrc.cjs",
        "svgrrc.mts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_svgr else 0
        "syncpackrc.mjs" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_syncpack else 0
        "taskfile.dist.yml" -> R.drawable.home_mit_taskfile
        "tsconfig.mjs.json" -> R.drawable.home_mit_tsconfig
        "tsdoc.json" -> R.drawable.home_mit_tsdoc
        "tsdown.config.cjs",
        "tsdown.config.mts" -> R.drawable.home_mit_tsdown
        "uno.config.js" -> R.drawable.home_mit_unocss
        "vagrantfile" -> R.drawable.home_mit_vagrant
        "vue.config.js" -> R.drawable.home_mit_vue_config
        "warp.md" -> if (light) R.drawable.home_mit_warp_light else R.drawable.home_mit_warp
        "webpack.config.babel.cjs",
        "webpack.config.babel.mts",
        "webpack.config.dev.js",
        "webpack.config.production.babel.ts",
        "webpack.config.staging.ts",
        "webpack.development.js",
        "webpack.production.js",
        "webpackfile.cts" -> R.drawable.home_mit_webpack
        "windi.config.cjs" -> R.drawable.home_mit_windicss
        "xamlstyler.json" -> R.drawable.home_mit_xaml
        "zeabur.json" -> if (light) R.drawable.home_mit_zeabur_light else R.drawable.home_mit_zeabur
        else -> 0
    }

    private fun named14(key: String, path: String, light: Boolean): Int = when (key) {
        "._sc_" -> R.drawable.home_mit_godot_assets
        ".autorc" -> if (light) R.drawable.home_mit_auto_light else R.drawable.home_mit_auto
        ".babel-plugin-macrosrc.jsonc",
        "babel-transform.js",
        "babel.config.mjs" -> R.drawable.home_mit_babel
        ".clang-format-ignore" -> R.drawable.home_mit_settings
        ".clinerules" -> R.drawable.home_mit_cline
        ".commitlintrc.js",
        "commitlint.config.mjs" -> R.drawable.home_mit_commitlint
        ".cracorc.jsonc" -> R.drawable.home_mit_craco
        ".env.test.local" -> R.drawable.home_mit_tune
        ".esformatter" -> R.drawable.home_mit_json
        ".eslintrc.cjs",
        ".eslintrc.mts",
        "eslint.config.json5",
        "eslint.config.ts" -> R.drawable.home_mit_eslint
        ".flowconfig" -> R.drawable.home_mit_flow
        ".gcloudignore" -> R.drawable.home_mit_gcp
        ".helmignore" -> R.drawable.home_mit_helm
        ".hgrc" -> R.drawable.home_mit_mercurial
        ".htaccess" -> R.drawable.home_mit_xml
        ".huskyrc.yaml" -> R.drawable.home_mit_husky
        ".knip.json" -> R.drawable.home_mit_knip
        ".lefthook.json",
        "lefthook-local.json",
        "lefthook.toml" -> R.drawable.home_mit_lefthook
        ".lighthouserc.js" -> R.drawable.home_mit_lighthouse
        ".lintstagedrc.json",
        "lint-staged.config.cjs" -> R.drawable.home_mit_lintstaged
        ".nestconfig.json" -> R.drawable.home_mit_nest
        ".npmrc" -> R.drawable.home_mit_npm
        ".pdm-python" -> R.drawable.home_mit_pdm
        ".postcssrc.mjs" -> R.drawable.home_mit_postcss
        ".posthtmlrc.cts",
        ".posthtmlrc.toml" -> R.drawable.home_mit_posthtml
        ".puppeteerrc.jsonc" -> R.drawable.home_mit_puppeteer
        ".release-it.ts",
        ".release-plz.toml" -> R.drawable.home_mit_rocket
        ".releaserc.cts",
        ".releaserc.toml",
        "release.config.js" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        ".stryker.conf.cjs",
        "stryker.config.cjs" -> R.drawable.home_mit_stryker
        ".stylelintrc.yaml" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        ".taskrc.yml" -> R.drawable.home_mit_taskfile
        ".tobimake" -> R.drawable.home_mit_tobimake
        ".vfl" -> R.drawable.home_mit_vfl
        "amplify.yml" -> R.drawable.home_mit_amplify
        "appraisals",
        "brewfile",
        "capfile" -> R.drawable.home_mit_ruby
        "babel-plugin-macrosrc.json5",
        "babel-plugin-macrosrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "bun.lock" -> if (light) R.drawable.home_mit_bun_light else R.drawable.home_mit_bun
        "chromatic.config.json" -> R.drawable.home_mit_chromatic
        "cname" -> R.drawable.home_mit_http
        "code_of_conduct" -> R.drawable.home_mit_conduct
        "compose.testing.yml",
        "docker-compose.testing.yaml",
        "docker-compose.worker.yaml",
        "dockerfile.testing" -> R.drawable.home_mit_docker
        "copyright.md",
        "licence-agpl",
        "license-agpl" -> R.drawable.home_mit_license
        "cracorc.json5",
        "cracorc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_craco else 0
        "cz.yaml" -> R.drawable.home_mit_commitizen
        "drizzle.config.prod.js" -> R.drawable.home_mit_drizzle
        "ecosystem.config.cjs",
        "ecosystem.config.mts" -> R.drawable.home_mit_pm2_ecosystem
        "esbuild.cjs",
        "esbuild.config.cjs",
        "esbuild.config.mts",
        "esbuild.mts",
        "esbuild.stage.cjs",
        "esbuild.stage.mts",
        "esbuild.test.cjs",
        "esbuild.test.mts" -> R.drawable.home_mit_esbuild
        "firebase.config.js" -> R.drawable.home_mit_firebase
        "gatsby-config.ts",
        "gatsby-node.ts" -> R.drawable.home_mit_gatsby
        "graphql.config.yaml" -> R.drawable.home_mit_graphql
        "graphqlrc.cts",
        "graphqlrc.toml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_graphql else 0
        "gruntfile.coffee" -> R.drawable.home_mit_grunt
        "hadolint.yaml" -> R.drawable.home_mit_hadolint
        "horusec-config.json" -> R.drawable.home_mit_horusec
        "jest-e2e.config.js",
        "jest-preset.cjs",
        "jest.e2e.config.cts" -> R.drawable.home_mit_jest
        "kcl.yml" -> R.drawable.home_mit_kcl
        "keystatic.config.tsx" -> R.drawable.home_mit_keystatic
        "lynx.config.js" -> R.drawable.home_mit_lynx
        "panda.config.mjs" -> R.drawable.home_mit_panda
        "phpstan.neon" -> R.drawable.home_mit_phpstan
        "plopfile.js" -> R.drawable.home_mit_plop
        "postcssrc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_postcss else 0
        "prettierrc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_prettier else 0
        "prisma.config.cjs",
        "prisma.config.mts",
        "prisma7.config.js" -> R.drawable.home_mit_prisma
        "puppeteerrc.json5",
        "puppeteerrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_puppeteer else 0
        "quasar.config.js" -> R.drawable.home_mit_quasar
        "readme" -> R.drawable.home_mit_readme
        "releaserc.json" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release else 0
        "roadmap.txt" -> R.drawable.home_mit_roadmap
        "rollup.config.dev.ts",
        "rollup.config.prod.vendor.js" -> R.drawable.home_mit_rollup
        "route.js",
        "router.js" -> R.drawable.home_mit_routing
        "rspack.config.mjs",
        "rstack.config.cts" -> R.drawable.home_mit_rstack
        "sentry.client.config.mjs" -> R.drawable.home_mit_sentry
        "svgrrc.json5",
        "svgrrc.ts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_svgr else 0
        "tsconfig.client.json",
        "tsconfig.dev.json",
        "tsconfig.lib.json",
        "tsconfig.spec.json" -> R.drawable.home_mit_tsconfig
        "tsdown.config.ts" -> R.drawable.home_mit_tsdown
        "webpack.config.babel.ts",
        "webpack.config.client.mjs",
        "webpack.config.common.mjs",
        "webpack.config.dev.babel.mjs",
        "webpack.config.dev.cts",
        "webpack.config.production.babel.cjs",
        "webpack.config.production.babel.mts",
        "webpack.config.staging.cjs",
        "webpack.config.staging.mts",
        "webpack.development.cts",
        "webpack.production.cts",
        "webpackfile.js" -> R.drawable.home_mit_webpack
        "windi.config.ts" -> R.drawable.home_mit_windicss
        "wrangler.toml" -> R.drawable.home_mit_wrangler
        "zshenv" -> R.drawable.home_mit_console
        else -> 0
    }

    private fun named15(key: String, path: String, light: Boolean): Int = when (key) {
        ".angular-cli.json" -> R.drawable.home_mit_angular
        ".astylerc" -> R.drawable.home_mit_astyle
        ".babel-plugin-macrosrc.cjs",
        ".babel-plugin-macrosrc.mts",
        "babel-plugin-macros.config.jsonc",
        "babel.config.yaml" -> R.drawable.home_mit_babel
        ".biome.jsonc" -> R.drawable.home_mit_biome
        ".coderabbit.yml" -> R.drawable.home_mit_coderabbit_ai
        ".commitlintrc.toml",
        "commitlint.config.yaml" -> R.drawable.home_mit_commitlint
        ".cracorc.cjs",
        ".cracorc.mts",
        "craco.config.jsonc" -> R.drawable.home_mit_craco
        ".cz.yml",
        "cz.json" -> R.drawable.home_mit_commitizen
        ".env.alpha",
        ".env.qa",
        ".env.stage" -> R.drawable.home_mit_tune
        ".eslintrc.json5" -> R.drawable.home_mit_eslint
        ".hadolint.yml" -> R.drawable.home_mit_hadolint
        ".happo.js" -> R.drawable.home_mit_happo
        ".hgignore" -> R.drawable.home_mit_mercurial
        ".huskyrc.json",
        ".huskyrc.mjs" -> R.drawable.home_mit_husky
        ".jshintrc" -> R.drawable.home_mit_json
        ".kl" -> R.drawable.home_mit_kl
        ".lefthook-local.yaml" -> R.drawable.home_mit_lefthook
        ".lintstagedrc.js" -> R.drawable.home_mit_lintstaged
        ".luacheckrc" -> R.drawable.home_mit_lua
        ".nano-staged.cjs" -> if (light) R.drawable.home_mit_nano_staged_light else R.drawable.home_mit_nano_staged
        ".npmignore" -> R.drawable.home_mit_npm
        ".oxfmtrc.json" -> R.drawable.home_mit_oxc
        ".percy.yml" -> R.drawable.home_mit_percy
        ".php_cs.dist",
        ".php_cs.php" -> R.drawable.home_mit_php_cs_fixer
        ".postcssrc.yaml" -> R.drawable.home_mit_postcss
        ".prettierrc.jsonc" -> R.drawable.home_mit_prettier
        ".puppeteerrc.cjs",
        ".puppeteerrc.mts",
        "puppeteer.config.jsonc" -> R.drawable.home_mit_puppeteer
        ".remarkrc" -> R.drawable.home_mit_remark
        ".renovaterc",
        "renovate.json5" -> R.drawable.home_mit_renovate
        ".rspec" -> R.drawable.home_mit_rspec
        ".stylelintrc.json",
        ".stylelintrc.mjs" -> if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint
        ".swift-format" -> R.drawable.home_mit_swift
        ".tazerc.json",
        "taze.config.js" -> R.drawable.home_mit_taze
        ".textlintrc" -> R.drawable.home_mit_textlint
        "appwrite.ts" -> R.drawable.home_mit_appwrite
        "astro.config.cjs",
        "astro.config.mts" -> R.drawable.home_mit_astro_config
        "babel-plugin-macrosrc",
        "babelrc.cts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_babel else 0
        "bashly-strings.yaml" -> if (hasIconParent(path, "src")) if (light) R.drawable.home_mit_bashly_strings_light else R.drawable.home_mit_bashly_strings else 0
        "bower.json" -> R.drawable.home_mit_bower
        "bunfig.toml" -> if (light) R.drawable.home_mit_bun_light else R.drawable.home_mit_bun
        "capacitor.config.ts" -> R.drawable.home_mit_capacitor
        "commit_editmsg" -> R.drawable.home_mit_git
        "commitlintrc.cts" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_commitlint else 0
        "compose.production.yml",
        "containerfile.testing",
        "containerfile.worker",
        "docker-compose.alpha.yml",
        "docker-compose.override.yml",
        "docker-compose.stage.yml",
        "docker-compose.yml" -> R.drawable.home_mit_docker
        "contentlayer.config.js" -> R.drawable.home_mit_contentlayer
        "contributing.rst" -> R.drawable.home_mit_contributing
        "copilot-instructions.md" -> if (light) R.drawable.home_mit_copilot_light else R.drawable.home_mit_copilot
        "copying.txt" -> R.drawable.home_mit_license
        "cracorc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_craco else 0
        "credits.rst" -> R.drawable.home_mit_credits
        "dangerfile" -> R.drawable.home_mit_ruby
        "deno.jsonc" -> if (light) R.drawable.home_mit_deno_light else R.drawable.home_mit_deno
        "eas.json" -> if (light) R.drawable.home_mit_expo_light else R.drawable.home_mit_expo
        "ember-cli-builds.js" -> R.drawable.home_mit_ember
        "esbuild.dev.mjs" -> R.drawable.home_mit_esbuild
        "eslintrc.jsonc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_eslint else 0
        "firebase.json" -> R.drawable.home_mit_firebase
        "garden.yml" -> R.drawable.home_mit_garden
        "googleservice-info.plist" -> R.drawable.home_mit_google
        "graphql.config.json",
        "graphql.config.mjs" -> R.drawable.home_mit_graphql
        "gruntfile.babel.js" -> R.drawable.home_mit_grunt
        "gulpfile.ts" -> R.drawable.home_mit_gulp
        "harmonix.config.ts" -> R.drawable.home_mit_harmonix
        "histoire.config.cjs",
        "histoire.config.mts" -> R.drawable.home_mit_histoire
        "huskyrc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_husky else 0
        "jest-e2e.json",
        "jest.preset.cjs" -> R.drawable.home_mit_jest
        "jvm.config" -> R.drawable.home_mit_maven
        "k8s.yaml",
        "kubernetes.yaml" -> R.drawable.home_mit_kubernetes
        "metro.config.json",
        "metro.config.mjs" -> R.drawable.home_mit_metro
        "nyc.config.js" -> R.drawable.home_mit_istanbul
        "opencode.json" -> if (light) R.drawable.home_mit_opencode_light else R.drawable.home_mit_opencode
        "pdm.toml" -> R.drawable.home_mit_pdm
        "phpunit-watcher.yml.dist",
        "phpunit.xml.dist" -> R.drawable.home_mit_phpunit
        "plastic.branchexplorer" -> R.drawable.home_mit_plastic
        "playwright-ct.config.cts",
        "playwright.config.base.js",
        "playwright.config.mjs" -> R.drawable.home_mit_playwright
        "posthtml.config.yml" -> R.drawable.home_mit_posthtml
        "protractor.conf.js" -> R.drawable.home_mit_protractor
        "puppeteerrc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_puppeteer else 0
        "release.config.toml" -> if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release
        "releaserc.js" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_semantic_release_light else R.drawable.home_mit_semantic_release else 0
        "rolldown.config.cjs",
        "rolldown.config.mts" -> R.drawable.home_mit_rolldown
        "rollup-config.mjs",
        "rollup.config.base.mjs",
        "rollup.config.js" -> R.drawable.home_mit_rollup
        "rspress.config.ts" -> R.drawable.home_mit_rstack
        "sentry.server.config.ts" -> R.drawable.home_mit_sentry
        "sha256sums" -> R.drawable.home_mit_key
        "snapcraft.yaml" -> R.drawable.home_mit_snapcraft
        "stylelintrc.yml" -> if (hasIconParent(path, ".config")) if (light) R.drawable.home_mit_stylelint_light else R.drawable.home_mit_stylelint else 0
        "svgr.config.jsonc" -> R.drawable.home_mit_svgr
        "svgrrc" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_svgr else 0
        "syncpackrc.yml" -> if (hasIconParent(path, ".config")) R.drawable.home_mit_syncpack else 0
        "tailwind.config.js",
        "tailwind.js" -> R.drawable.home_mit_tailwindcss
        "todo.md" -> R.drawable.home_mit_todo
        "trigger.config.js" -> R.drawable.home_mit_trigger
        "tsconfig.web.json" -> R.drawable.home_mit_tsconfig
        "tsdown.config" -> R.drawable.home_mit_tsdown
        "vercel.json" -> if (light) R.drawable.home_mit_vercel_light else R.drawable.home_mit_vercel
        "vetur.config.ts" -> R.drawable.home_mit_vue_config
        "vitest.config.cts",
        "vitest.e2e.config.cjs",
        "vitest.e2e.config.mts" -> R.drawable.home_mit_vitest
        "webpack.config.base.babel.js",
        "webpack.config.prod.babel.ts",
        "webpack.config.renderer.ts",
        "webpack.config.server.ts",
        "webpack.dist.mjs",
        "webpack.mix.cts",
        "webpack.prod.config.js",
        "webpack.prod.js" -> R.drawable.home_mit_webpack
        "werf-giterminism.yml",
        "werf-includes.yaml" -> R.drawable.home_mit_werf
        "zeabur.toml" -> if (light) R.drawable.home_mit_zeabur_light else R.drawable.home_mit_zeabur
        "zizmor.yml" -> R.drawable.home_mit_zizmor
        "zlogin",
        "zprofile" -> R.drawable.home_mit_console
        else -> 0
    }

    fun extension(key: String, light: Boolean): Int = when (key.hashCode() and 15) {
        0 -> extension0(key, light)
        1 -> extension1(key, light)
        2 -> extension2(key, light)
        3 -> extension3(key, light)
        4 -> extension4(key, light)
        5 -> extension5(key, light)
        6 -> extension6(key, light)
        7 -> extension7(key, light)
        8 -> extension8(key, light)
        9 -> extension9(key, light)
        10 -> extension10(key, light)
        11 -> extension11(key, light)
        12 -> extension12(key, light)
        13 -> extension13(key, light)
        14 -> extension14(key, light)
        else -> extension15(key, light)
    }

    private fun extension0(key: String, light: Boolean): Int = when (key) {
        "aa",
        "cda",
        "mp3",
        "mpc",
        "stap" -> R.drawable.home_mit_audio
        "abap" -> R.drawable.home_mit_abap
        "ads" -> R.drawable.home_mit_ada
        "astore" -> R.drawable.home_mit_sas
        "bdb",
        "ndf",
        "orc" -> R.drawable.home_mit_database
        "bean" -> R.drawable.home_mit_beancount
        "blend1" -> R.drawable.home_mit_blender
        "br",
        "lzma2" -> R.drawable.home_mit_zip
        "c3" -> R.drawable.home_mit_c3
        "cc",
        "ii" -> R.drawable.home_mit_cpp
        "cer" -> R.drawable.home_mit_certificate
        "cfc" -> R.drawable.home_mit_coldfusion
        "clip",
        "cur" -> R.drawable.home_mit_image
        "cmj" -> R.drawable.home_mit_bucklescript
        "component.ts" -> R.drawable.home_mit_angular_component
        "cs" -> R.drawable.home_mit_csharp
        "docker-compose.yaml" -> R.drawable.home_mit_docker
        "docx" -> R.drawable.home_mit_word
        "exs",
        "heex" -> R.drawable.home_mit_elixir
        "flv",
        "wmv" -> R.drawable.home_mit_video
        "freezed.dart" -> R.drawable.home_mit_dart_generated
        "garden.yaml" -> R.drawable.home_mit_garden
        "geom.glsl",
        "gs.glsl",
        "rcall" -> R.drawable.home_mit_shader
        "gpi" -> R.drawable.home_mit_gnuplot
        "hh" -> R.drawable.home_mit_hpp
        "hjson" -> R.drawable.home_mit_hjson
        "hx" -> R.drawable.home_mit_haxe
        "instructions.md" -> R.drawable.home_mit_instructions
        "jsonld" -> R.drawable.home_mit_json
        "ksh",
        "zshrc" -> R.drawable.home_mit_console
        "ltx" -> R.drawable.home_mit_tex
        "mctemplate" -> R.drawable.home_mit_minecraft
        "mist.ts" -> R.drawable.home_mit_mist
        "mm" -> R.drawable.home_mit_objective_cpp
        "ntf" -> R.drawable.home_mit_font
        "olm",
        "ost" -> R.drawable.home_mit_email
        "opml",
        "vbproj.user",
        "xpdl",
        "xquery" -> R.drawable.home_mit_xml
        "p" -> R.drawable.home_mit_prolog
        "pdm.lock" -> R.drawable.home_mit_pdm
        "pine" -> R.drawable.home_mit_pinejs
        "podspec",
        "rb" -> R.drawable.home_mit_ruby
        "pp" -> R.drawable.home_mit_puppet
        "prefs",
        "props",
        "tool-versions" -> R.drawable.home_mit_settings
        "prisma" -> R.drawable.home_mit_prisma
        "psd1" -> R.drawable.home_mit_powershell
        "pyi" -> R.drawable.home_mit_python
        "qed" -> R.drawable.home_mit_disc
        "rbxmk.lua" -> R.drawable.home_mit_rbxmk
        "res" -> R.drawable.home_mit_rescript
        "san" -> R.drawable.home_mit_san
        "sc" -> R.drawable.home_mit_scala
        "scss" -> R.drawable.home_mit_sass
        "secret" -> R.drawable.home_mit_key
        "sldasm" -> R.drawable.home_mit_3d
        "sol" -> R.drawable.home_mit_solidity
        "ss" -> R.drawable.home_mit_scheme
        "story.tsx" -> R.drawable.home_mit_storybook
        "sub" -> R.drawable.home_mit_subtitles
        "swcrc" -> R.drawable.home_mit_swc
        "test.cjs" -> R.drawable.home_mit_test_js
        "test.mts" -> R.drawable.home_mit_test_ts
        "tpl" -> R.drawable.home_mit_smarty
        "txt" -> R.drawable.home_mit_document
        "vala" -> R.drawable.home_mit_vala
        "wpy" -> R.drawable.home_mit_wepy
        "yy",
        "yyp" -> R.drawable.home_mit_gamemaker
        else -> 0
    }

    private fun extension1(key: String, light: Boolean): Int = when (key) {
        "001",
        "deb",
        "zip" -> R.drawable.home_mit_zip
        "a" -> R.drawable.home_mit_lib
        "ac3",
        "cdr",
        "gsm",
        "mui",
        "mxl",
        "wfp" -> R.drawable.home_mit_audio
        "acds" -> R.drawable.home_mit_abap
        "asc" -> R.drawable.home_mit_key
        "ass",
        "ssa" -> R.drawable.home_mit_subtitles
        "atom",
        "fxml",
        "storyboard",
        "tmlanguage",
        "xib",
        "xsl" -> R.drawable.home_mit_xml
        "bench.jsx" -> R.drawable.home_mit_bench_jsx
        "blend",
        "blend2" -> R.drawable.home_mit_blender
        "clj" -> R.drawable.home_mit_clojure
        "cmake" -> R.drawable.home_mit_cmake
        "coafile" -> R.drawable.home_mit_coala
        "controller.ts" -> R.drawable.home_mit_controller
        "cpn" -> R.drawable.home_mit_coloredpetrinets
        "css.cjs" -> R.drawable.home_mit_vanilla_extract
        "css.map" -> R.drawable.home_mit_css_map
        "cy.js" -> R.drawable.home_mit_test_js
        "cy.tsx" -> R.drawable.home_mit_test_jsx
        "fhtml" -> R.drawable.home_mit_velocity
        "frm" -> R.drawable.home_mit_database
        "godot" -> R.drawable.home_mit_godot_assets
        "hcl" -> if (light) R.drawable.home_mit_hcl_light else R.drawable.home_mit_hcl
        "hjs" -> R.drawable.home_mit_handlebars
        "htm",
        "volt" -> R.drawable.home_mit_html
        "iiq",
        "jpg",
        "mos",
        "pef",
        "tif" -> R.drawable.home_mit_image
        "kl" -> R.drawable.home_mit_kl
        "m2v",
        "mp4" -> R.drawable.home_mit_video
        "mdx" -> R.drawable.home_mit_mdx
        "module.ts" -> R.drawable.home_mit_angular
        "moon" -> R.drawable.home_mit_moonscript
        "mrf",
        "odttf",
        "otf" -> R.drawable.home_mit_font
        "msg",
        "pst" -> R.drawable.home_mit_email
        "ndst.yml" -> R.drawable.home_mit_ndst
        "pde" -> R.drawable.home_mit_processing
        "pkl" -> R.drawable.home_mit_pkl
        "plot" -> R.drawable.home_mit_gnuplot
        "ppa" -> R.drawable.home_mit_powerpoint
        "prompt.md" -> R.drawable.home_mit_prompt
        "psc1" -> R.drawable.home_mit_powershell
        "psd" -> if (light) R.drawable.home_mit_adobe_photoshop_light else R.drawable.home_mit_adobe_photoshop
        "quokka.js",
        "quokka.tsx" -> R.drawable.home_mit_quokka
        "rc" -> R.drawable.home_mit_rc
        "red" -> R.drawable.home_mit_red
        "resolver.js" -> R.drawable.home_mit_angular_resolver
        "ron",
        "rs" -> R.drawable.home_mit_rust
        "routing.js",
        "routing.tsx" -> R.drawable.home_mit_routing
        "sig" -> R.drawable.home_mit_sml
        "slang",
        "tesc",
        "vert" -> R.drawable.home_mit_shader
        "slnf" -> R.drawable.home_mit_visualstudio
        "smk" -> R.drawable.home_mit_snakemake
        "sublime-project" -> R.drawable.home_mit_sublime
        "twig" -> R.drawable.home_mit_twig
        "unitypackage" -> R.drawable.home_mit_unity
        "windi" -> R.drawable.home_mit_windicss
        "wrl" -> R.drawable.home_mit_3d
        "xprofile" -> R.drawable.home_mit_console
        "yang" -> R.drawable.home_mit_yang
        else -> 0
    }

    private fun extension2(key: String, light: Boolean): Int = when (key) {
        "3ds",
        "ac",
        "dxf",
        "prt",
        "vdp" -> R.drawable.home_mit_3d
        "aab" -> R.drawable.home_mit_android
        "abc" -> R.drawable.home_mit_abc
        "accde" -> R.drawable.home_mit_database
        "act" -> R.drawable.home_mit_palette
        "as" -> R.drawable.home_mit_actionscript
        "ast" -> R.drawable.home_mit_sas
        "avif",
        "cap",
        "mrw" -> R.drawable.home_mit_image
        "azure-pipelines-main.yaml",
        "azure-pipelines.yaml" -> R.drawable.home_mit_azure_pipelines
        "b" -> R.drawable.home_mit_brainfuck
        "bazel" -> R.drawable.home_mit_bazel
        "binsource" -> R.drawable.home_mit_assembly
        "blade.php" -> R.drawable.home_mit_laravel
        "brotli",
        "lzma",
        "tlz",
        "xz" -> R.drawable.home_mit_zip
        "cdc" -> R.drawable.home_mit_cadence
        "cds" -> R.drawable.home_mit_cds
        "cfml" -> R.drawable.home_mit_coldfusion
        "cljc",
        "cljs" -> R.drawable.home_mit_clojure
        "config",
        "repo" -> R.drawable.home_mit_settings
        "cts" -> R.drawable.home_mit_typescript
        "cu" -> R.drawable.home_mit_cuda
        "d.mts" -> R.drawable.home_mit_typescript_def
        "dita",
        "publishsettings",
        "rss",
        "shproj",
        "targets",
        "wxs",
        "xbl" -> R.drawable.home_mit_xml
        "dockerignore" -> R.drawable.home_mit_docker
        "drone.yml" -> if (light) R.drawable.home_mit_drone_light else R.drawable.home_mit_drone
        "duc" -> R.drawable.home_mit_duc
        "efs",
        "mod",
        "rf64" -> R.drawable.home_mit_audio
        "excalidraw" -> R.drawable.home_mit_excalidraw
        "exe" -> R.drawable.home_mit_exe
        "f95" -> R.drawable.home_mit_fortran
        "frag",
        "fx",
        "rchit" -> R.drawable.home_mit_shader
        "gdextension",
        "gdns",
        "gdshader" -> R.drawable.home_mit_godot_assets
        "gifv",
        "mpe" -> R.drawable.home_mit_video
        "gitlab-ci.yml" -> R.drawable.home_mit_gitlab
        "gql" -> R.drawable.home_mit_graphql
        "grm",
        "sml" -> R.drawable.home_mit_sml
        "guard.js" -> R.drawable.home_mit_angular_guard
        "interceptor.js" -> R.drawable.home_mit_angular_interceptor
        "ipy" -> R.drawable.home_mit_python
        "java" -> R.drawable.home_mit_java
        "jenkinsfile" -> R.drawable.home_mit_jenkins
        "jinja-html" -> if (light) R.drawable.home_mit_jinja_light else R.drawable.home_mit_jinja
        "jl" -> R.drawable.home_mit_julia
        "latex" -> R.drawable.home_mit_tex
        "lbx" -> R.drawable.home_mit_lbx
        "marko" -> R.drawable.home_mit_markojs
        "mint" -> R.drawable.home_mit_mint
        "mjs.snap" -> R.drawable.home_mit_test_js
        "mo",
        "xlf" -> R.drawable.home_mit_i18n
        "nim" -> R.drawable.home_mit_nim
        "nuspec" -> R.drawable.home_mit_nuget
        "pac" -> R.drawable.home_mit_javascript
        "pas" -> R.drawable.home_mit_pascal
        "pdf" -> R.drawable.home_mit_pdf
        "peg" -> R.drawable.home_mit_peg
        "php.bubble" -> R.drawable.home_mit_dinophp
        "pipeline" -> R.drawable.home_mit_pipeline
        "pug" -> R.drawable.home_mit_pug
        "qs" -> R.drawable.home_mit_qsharp
        "r",
        "rhistory",
        "rt" -> R.drawable.home_mit_r
        "service.js" -> R.drawable.home_mit_angular_service
        "spec.ts" -> R.drawable.home_mit_test_ts
        "story.js" -> R.drawable.home_mit_storybook
        "svelte.ts" -> R.drawable.home_mit_svelte_ts
        "swf" -> R.drawable.home_mit_flash
        "tape" -> R.drawable.home_mit_tape
        "tf",
        "tfbackend" -> R.drawable.home_mit_terraform
        "tobi" -> R.drawable.home_mit_tobi
        "vcxitems.filters" -> R.drawable.home_mit_visualstudio
        "vhd" -> R.drawable.home_mit_verilog
        else -> 0
    }

    private fun extension3(key: String, light: Boolean): Int = when (key) {
        ".ncurc.json" -> R.drawable.home_mit_dependencies_update
        "7z",
        "bzip2",
        "cpio",
        "gz",
        "ntfs",
        "rar" -> R.drawable.home_mit_zip
        "aac",
        "mka",
        "sdt",
        "tg",
        "wpl" -> R.drawable.home_mit_audio
        "ad" -> R.drawable.home_mit_asciidoc
        "ase",
        "cr2",
        "dcr",
        "dds",
        "img",
        "jng",
        "ktx2",
        "nrw",
        "orf",
        "r3d",
        "sr2" -> R.drawable.home_mit_image
        "bash_aliases",
        "eclass" -> R.drawable.home_mit_console
        "bst" -> R.drawable.home_mit_bibtex_style
        "c" -> R.drawable.home_mit_c
        "c++",
        "cpp",
        "cxx" -> R.drawable.home_mit_cpp
        "cff",
        "eyml" -> R.drawable.home_mit_yaml
        "compose.yaml",
        "containerignore" -> R.drawable.home_mit_docker
        "copilotmd",
        "litcoffee",
        "rst" -> R.drawable.home_mit_markdown
        "css" -> R.drawable.home_mit_css
        "cue" -> R.drawable.home_mit_cue
        "edb",
        "rpmsg" -> R.drawable.home_mit_email
        "ex" -> R.drawable.home_mit_elixir
        "feather" -> R.drawable.home_mit_database
        "gmi" -> R.drawable.home_mit_gemini
        "gpl" -> R.drawable.home_mit_palette
        "imba" -> R.drawable.home_mit_imba
        "launch",
        "xslt" -> R.drawable.home_mit_xml
        "mcproject",
        "mine" -> R.drawable.home_mit_minecraft
        "mlb" -> R.drawable.home_mit_sml
        "mpv",
        "qt" -> R.drawable.home_mit_video
        "msi" -> R.drawable.home_mit_exe
        "pcss",
        "sss" -> R.drawable.home_mit_postcss
        "potx",
        "pps" -> R.drawable.home_mit_powerpoint
        "prop",
        "properties",
        "settings",
        "sln.dotsettings" -> R.drawable.home_mit_settings
        "psdt" -> if (light) R.drawable.home_mit_adobe_photoshop_light else R.drawable.home_mit_adobe_photoshop
        "pssc" -> R.drawable.home_mit_powershell
        "psv" -> R.drawable.home_mit_table
        "px.hlsl",
        "tese" -> R.drawable.home_mit_shader
        "rbs",
        "ru" -> R.drawable.home_mit_ruby
        "rbxmx" -> R.drawable.home_mit_roblox
        "re" -> R.drawable.home_mit_reason
        "routes.ts" -> R.drawable.home_mit_routing
        "s" -> R.drawable.home_mit_assembly
        "skills.md" -> R.drawable.home_mit_skill
        "slnx",
        "wixproj" -> R.drawable.home_mit_visualstudio
        "spec.mjs" -> R.drawable.home_mit_test_js
        "sv" -> R.drawable.home_mit_verilog
        "swift" -> R.drawable.home_mit_swift
        "test.jsx" -> R.drawable.home_mit_test_jsx
        "ttc" -> R.drawable.home_mit_font
        "tw",
        "twee" -> R.drawable.home_mit_twine
        "webmanifest" -> R.drawable.home_mit_json
        "xhtml" -> R.drawable.home_mit_html
        else -> 0
    }

    private fun extension4(key: String, light: Boolean): Int = when (key) {
        ".ncurc.js" -> R.drawable.home_mit_dependencies_update
        "ahk" -> R.drawable.home_mit_autohotkey
        "argus",
        "mitigus" -> R.drawable.home_mit_assembly
        "aux",
        "tikz" -> R.drawable.home_mit_tex
        "avi",
        "mov",
        "mpg" -> R.drawable.home_mit_video
        "bas" -> R.drawable.home_mit_visualstudio
        "bash",
        "tcsh",
        "zlogout" -> R.drawable.home_mit_console
        "bench.cts" -> R.drawable.home_mit_bench_ts
        "bf" -> R.drawable.home_mit_brainfuck
        "bzl" -> R.drawable.home_mit_bazel
        "cab" -> R.drawable.home_mit_zip
        "capnp" -> R.drawable.home_mit_capnp
        "cert" -> R.drawable.home_mit_certificate
        "cfg",
        "conf",
        "ini" -> R.drawable.home_mit_settings
        "clojure" -> R.drawable.home_mit_clojure
        "compute.hlsl",
        "rahit" -> R.drawable.home_mit_shader
        "cr3",
        "dcs",
        "gif",
        "jxr",
        "ptx" -> R.drawable.home_mit_image
        "css.js",
        "css.jsx" -> R.drawable.home_mit_vanilla_extract
        "d" -> R.drawable.home_mit_d
        "dll" -> R.drawable.home_mit_dll
        "dsc" -> R.drawable.home_mit_denizenscript
        "dss",
        "wproj" -> R.drawable.home_mit_audio
        "dtd",
        "owl",
        "rdf",
        "wsdl" -> R.drawable.home_mit_xml
        "dwg",
        "sab",
        "ste",
        "usdz" -> R.drawable.home_mit_3d
        "e2e-spec.cts",
        "test-d.ts" -> R.drawable.home_mit_test_ts
        "ecr" -> if (light) R.drawable.home_mit_crystal_light else R.drawable.home_mit_crystal
        "eml",
        "emlx" -> R.drawable.home_mit_email
        "excalidraw.json" -> R.drawable.home_mit_excalidraw
        "exrc",
        "gvimrc" -> R.drawable.home_mit_vim
        "fdb",
        "parquet" -> R.drawable.home_mit_database
        "fig" -> R.drawable.home_mit_figma
        "fonts" -> R.drawable.home_mit_font
        "gdnlib" -> R.drawable.home_mit_godot_assets
        "jsonl" -> R.drawable.home_mit_json
        "log" -> R.drawable.home_mit_log
        "mdtext" -> R.drawable.home_mit_markdown
        "mjs.map" -> R.drawable.home_mit_javascript_map
        "mlton" -> R.drawable.home_mit_sml
        "mmd" -> R.drawable.home_mit_mermaid
        "nb" -> R.drawable.home_mit_mathematica
        "ppt",
        "pptx" -> R.drawable.home_mit_powerpoint
        "psrc" -> R.drawable.home_mit_powershell
        "pt",
        "pth" -> R.drawable.home_mit_pytorch
        "puml" -> if (light) R.drawable.home_mit_uml_light else R.drawable.home_mit_uml
        "rbxl",
        "rbxlx" -> R.drawable.home_mit_roblox
        "reg" -> R.drawable.home_mit_regedit
        "rest" -> R.drawable.home_mit_http
        "route.ts",
        "route.tsx" -> R.drawable.home_mit_routing
        "rtf" -> R.drawable.home_mit_word
        "sashdat" -> R.drawable.home_mit_sas
        "sha256sum" -> R.drawable.home_mit_key
        "skill.md" -> R.drawable.home_mit_skill
        "smali" -> R.drawable.home_mit_android
        "spwn" -> R.drawable.home_mit_spwn
        "stories.js",
        "stories.jsx" -> R.drawable.home_mit_storybook
        "styl" -> R.drawable.home_mit_stylus
        "svg" -> R.drawable.home_mit_svg
        "sw" -> R.drawable.home_mit_sway
        "swagger.yml" -> R.drawable.home_mit_swagger
        "t" -> R.drawable.home_mit_perl
        "taskfile.yaml" -> R.drawable.home_mit_taskfile
        "tcc" -> R.drawable.home_mit_hpp
        "test-d.tsx" -> R.drawable.home_mit_test_jsx
        "tf.json" -> R.drawable.home_mit_terraform
        "tpp",
        "txx" -> R.drawable.home_mit_cpp
        "wasm" -> R.drawable.home_mit_webassembly
        "xht" -> R.drawable.home_mit_html
        else -> 0
    }

    private fun extension5(key: String, light: Boolean): Int = when (key) {
        "applescript" -> R.drawable.home_mit_applescript
        "aseprite",
        "bmp",
        "ico",
        "rwz",
        "tiff" -> R.drawable.home_mit_image
        "awk",
        "bash_logout",
        "bashrc",
        "bat",
        "sh",
        "tcshrc" -> R.drawable.home_mit_console
        "bcf" -> R.drawable.home_mit_bibliography
        "bru" -> R.drawable.home_mit_bruno
        "ch" -> R.drawable.home_mit_advpl_include
        "code-workspace" -> R.drawable.home_mit_vscode
        "compute.glsl",
        "fragmentshader",
        "pixel.hlsl",
        "psh",
        "shader" -> R.drawable.home_mit_shader
        "crt" -> R.drawable.home_mit_certificate
        "db3",
        "gdb",
        "sdf" -> R.drawable.home_mit_database
        "def" -> R.drawable.home_mit_dotjs
        "dhallb" -> R.drawable.home_mit_dhall
        "diff" -> R.drawable.home_mit_diff
        "dmn",
        "jmx",
        "xliff" -> R.drawable.home_mit_xml
        "ec3" -> R.drawable.home_mit_audio
        "egg" -> R.drawable.home_mit_python_misc
        "erb" -> R.drawable.home_mit_ruby
        "gemini" -> R.drawable.home_mit_gemini
        "hfs",
        "lha",
        "tar" -> R.drawable.home_mit_zip
        "iam",
        "igs",
        "wire" -> R.drawable.home_mit_3d
        "iso" -> R.drawable.home_mit_disc
        "lib" -> R.drawable.home_mit_lib
        "m2" -> R.drawable.home_mit_macaulay2
        "mpeg" -> R.drawable.home_mit_video
        "nupkg" -> R.drawable.home_mit_nuget
        "openapi.yaml" -> if (light) R.drawable.home_mit_openapi_light else R.drawable.home_mit_openapi
        "option" -> R.drawable.home_mit_settings
        "pod",
        "psgi" -> R.drawable.home_mit_perl
        "pot" -> R.drawable.home_mit_i18n
        "ppsx" -> R.drawable.home_mit_powerpoint
        "prg" -> R.drawable.home_mit_foxpro
        "prw" -> R.drawable.home_mit_advpl
        "pu" -> if (light) R.drawable.home_mit_uml_light else R.drawable.home_mit_uml
        "rbxm" -> R.drawable.home_mit_roblox
        "rbxmk.luau" -> R.drawable.home_mit_rbxmk
        "routes.tsx" -> R.drawable.home_mit_routing
        "sas" -> R.drawable.home_mit_sas
        "sbt" -> R.drawable.home_mit_sbt
        "schema.json" -> R.drawable.home_mit_json_schema
        "sln",
        "vba",
        "vcxitems",
        "vcxproj.filters" -> R.drawable.home_mit_visualstudio
        "srt" -> R.drawable.home_mit_subtitles
        "steadybit.yml" -> R.drawable.home_mit_steadybit
        "svh" -> R.drawable.home_mit_verilog
        "svx" -> R.drawable.home_mit_mdsvex
        "swiftdoc" -> R.drawable.home_mit_swift
        "synctex.gz" -> R.drawable.home_mit_tex
        "test.js" -> R.drawable.home_mit_test_js
        "tsconfig.json" -> R.drawable.home_mit_tsconfig
        "unity" -> R.drawable.home_mit_unity
        "vbox" -> R.drawable.home_mit_virtual
        "ved" -> R.drawable.home_mit_vedic
        "verse" -> if (light) R.drawable.home_mit_verse_light else R.drawable.home_mit_verse
        "wl" -> R.drawable.home_mit_wolframlanguage
        else -> 0
    }

    private fun extension6(key: String, light: Boolean): Int = when (key) {
        "123dx",
        "sat",
        "usd" -> R.drawable.home_mit_3d
        "amr",
        "ape",
        "mmf",
        "mtm" -> R.drawable.home_mit_audio
        "arw",
        "braw",
        "fff",
        "pgm",
        "pxn" -> R.drawable.home_mit_image
        "axml",
        "xoml" -> R.drawable.home_mit_xml
        "clo" -> R.drawable.home_mit_tex
        "coarc" -> R.drawable.home_mit_coala
        "cob" -> R.drawable.home_mit_cobol
        "code-snippets",
        "code-workplace" -> R.drawable.home_mit_vscode
        "csv" -> R.drawable.home_mit_table
        "cts.snap",
        "test.cts" -> R.drawable.home_mit_test_ts
        "cuh" -> R.drawable.home_mit_cuda
        "elm" -> R.drawable.home_mit_elm
        "esd",
        "lz4",
        "tbz2",
        "txz",
        "tz" -> R.drawable.home_mit_zip
        "f",
        "f77" -> R.drawable.home_mit_fortran
        "feature" -> R.drawable.home_mit_cucumber
        "fxh" -> R.drawable.home_mit_shader
        "g.dart" -> R.drawable.home_mit_dart_generated
        "gml" -> R.drawable.home_mit_gamemaker
        "ipynb" -> R.drawable.home_mit_jupyter
        "jenkins" -> R.drawable.home_mit_jenkins
        "jinja" -> if (light) R.drawable.home_mit_jinja_light else R.drawable.home_mit_jinja
        "kql" -> R.drawable.home_mit_kusto
        "lean" -> R.drawable.home_mit_lean
        "mjs" -> R.drawable.home_mit_javascript
        "mkd" -> R.drawable.home_mit_markdown
        "ms" -> R.drawable.home_mit_assembly
        "openapi.json" -> if (light) R.drawable.home_mit_openapi_light else R.drawable.home_mit_openapi
        "pest" -> R.drawable.home_mit_peg
        "prompts.md" -> R.drawable.home_mit_prompt
        "prx" -> R.drawable.home_mit_advpl
        "purs" -> R.drawable.home_mit_purescript
        "rei" -> R.drawable.home_mit_reason
        "ruleset" -> R.drawable.home_mit_visualstudio
        "scala" -> R.drawable.home_mit_scala
        "sigstore.json" -> R.drawable.home_mit_verified
        "slint" -> R.drawable.home_mit_slint
        "sln.dotsettings.user" -> R.drawable.home_mit_settings
        "spec.tsx" -> R.drawable.home_mit_test_jsx
        "st.css" -> R.drawable.home_mit_stylable
        "stories.mdx",
        "stories.svelte",
        "story.jsx" -> R.drawable.home_mit_storybook
        "sy" -> R.drawable.home_mit_siyuan
        "terraformignore" -> R.drawable.home_mit_terraform
        "tldr" -> if (light) R.drawable.home_mit_tldraw_light else R.drawable.home_mit_tldraw
        "todo" -> R.drawable.home_mit_todo
        "ttf" -> R.drawable.home_mit_font
        "v" -> R.drawable.home_mit_vlang
        "vhdx" -> R.drawable.home_mit_verilog
        "vtt" -> R.drawable.home_mit_subtitles
        "vue" -> R.drawable.home_mit_vue
        "xcplayground" -> R.drawable.home_mit_swift
        "ц" -> R.drawable.home_mit_tsil
        else -> 0
    }

    private fun extension7(key: String, light: Boolean): Int = when (key) {
        ".wakatime-project" -> if (light) R.drawable.home_mit_wakatime_light else R.drawable.home_mit_wakatime
        "adoc",
        "asciidoc" -> R.drawable.home_mit_asciidoc
        "ascx",
        "xml" -> R.drawable.home_mit_xml
        "azcli" -> R.drawable.home_mit_azure
        "bench.js" -> R.drawable.home_mit_bench_js
        "bicep" -> R.drawable.home_mit_bicep
        "bin",
        "dat" -> R.drawable.home_mit_hex
        "cljx" -> R.drawable.home_mit_clojure
        "compute",
        "geometryshader" -> R.drawable.home_mit_shader
        "cpt",
        "gbr",
        "heic",
        "raf",
        "rwl",
        "srf" -> R.drawable.home_mit_image
        "csharp" -> R.drawable.home_mit_csharp
        "ctx" -> R.drawable.home_mit_context
        "cy.jsx" -> R.drawable.home_mit_test_jsx
        "cy.ts" -> R.drawable.home_mit_test_ts
        "dblite3",
        "pgsql",
        "pkb",
        "sqlite3" -> R.drawable.home_mit_database
        "dex" -> R.drawable.home_mit_android
        "e2e-spec.js" -> R.drawable.home_mit_test_js
        "f3d",
        "gltf",
        "ige",
        "obj",
        "pmd",
        "sldprt" -> R.drawable.home_mit_3d
        "far",
        "lz5",
        "tgz",
        "tzst" -> R.drawable.home_mit_zip
        "gradle" -> R.drawable.home_mit_gradle
        "gram" -> R.drawable.home_mit_peg
        "graphcool" -> R.drawable.home_mit_graphcool
        "hurl" -> R.drawable.home_mit_hurl
        "idr" -> R.drawable.home_mit_idris
        "inl" -> R.drawable.home_mit_hpp
        "instruction.md" -> R.drawable.home_mit_instructions
        "js.map" -> R.drawable.home_mit_javascript_map
        "jsp" -> R.drawable.home_mit_java
        "lhs" -> R.drawable.home_mit_haskell
        "ls" -> R.drawable.home_mit_livescript
        "mak" -> R.drawable.home_mit_settings
        "markdn",
        "md" -> R.drawable.home_mit_markdown
        "merlin" -> R.drawable.home_mit_merlin
        "mojo" -> R.drawable.home_mit_mojo
        "nu",
        "yash_profile" -> R.drawable.home_mit_console
        "pgn" -> if (light) R.drawable.home_mit_chess_light else R.drawable.home_mit_chess
        "psm1" -> R.drawable.home_mit_powershell
        "pwn" -> R.drawable.home_mit_pawn
        "quokka.jsx",
        "quokka.ts" -> R.drawable.home_mit_quokka
        "rej" -> R.drawable.home_mit_diff
        "resolver.ts" -> R.drawable.home_mit_angular_resolver
        "rmvb" -> R.drawable.home_mit_video
        "routing.jsx",
        "routing.ts" -> R.drawable.home_mit_routing
        "rprofile" -> R.drawable.home_mit_r
        "sas7bdat" -> R.drawable.home_mit_sas
        "sbv" -> R.drawable.home_mit_subtitles
        "sesx" -> R.drawable.home_mit_audio
        "sha256" -> R.drawable.home_mit_key
        "sui" -> R.drawable.home_mit_font
        "swiftdeps" -> R.drawable.home_mit_swift
        "tex" -> R.drawable.home_mit_tex
        "tsv" -> R.drawable.home_mit_table
        "use" -> R.drawable.home_mit_sml
        "vbhtml" -> R.drawable.home_mit_razor
        "vbs" -> R.drawable.home_mit_visualstudio
        "vm" -> R.drawable.home_mit_velocity
        "yaml",
        "yaml-tmlanguage" -> R.drawable.home_mit_yaml
        else -> 0
    }

    private fun extension8(key: String, light: Boolean): Int = when (key) {
        "4th",
        "frt" -> R.drawable.home_mit_forth
        "aax",
        "aiff",
        "caf",
        "flac",
        "mid" -> R.drawable.home_mit_audio
        "ai" -> if (light) R.drawable.home_mit_adobe_illustrator_light else R.drawable.home_mit_adobe_illustrator
        "apib" -> R.drawable.home_mit_apiblueprint
        "ari",
        "crw",
        "drf",
        "eps",
        "jpeg",
        "raw",
        "srw" -> R.drawable.home_mit_image
        "bbx" -> R.drawable.home_mit_bbx
        "bench.mjs" -> R.drawable.home_mit_bench_js
        "cairo" -> R.drawable.home_mit_cairo
        "cake" -> R.drawable.home_mit_cake
        "class" -> R.drawable.home_mit_javaclass
        "coco" -> R.drawable.home_mit_coconut
        "code-search" -> R.drawable.home_mit_search
        "csh",
        "fish" -> R.drawable.home_mit_console
        "csx" -> R.drawable.home_mit_csharp
        "d.cts" -> R.drawable.home_mit_typescript_def
        "dae",
        "skp",
        "smb",
        "vac" -> R.drawable.home_mit_3d
        "dbf",
        "debugsymbols",
        "myd",
        "pks" -> R.drawable.home_mit_database
        "directive.js" -> R.drawable.home_mit_angular_directive
        "doc" -> R.drawable.home_mit_word
        "dtx" -> R.drawable.home_mit_dtx
        "e2e-spec.mjs" -> R.drawable.home_mit_test_js
        "eex" -> R.drawable.home_mit_elixir
        "epub" -> R.drawable.home_mit_epub
        "es6" -> R.drawable.home_mit_javascript
        "excalidraw.svg" -> R.drawable.home_mit_excalidraw
        "fsscript" -> R.drawable.home_mit_fsharp
        "go" -> R.drawable.home_mit_go
        "guard.ts" -> R.drawable.home_mit_angular_guard
        "h" -> R.drawable.home_mit_h
        "h++",
        "hp",
        "hpp",
        "hxx" -> R.drawable.home_mit_hpp
        "haml" -> R.drawable.home_mit_haml
        "handlebars",
        "mustache" -> R.drawable.home_mit_handlebars
        "hdd",
        "qcow2" -> R.drawable.home_mit_disc
        "http" -> R.drawable.home_mit_http
        "ilk" -> R.drawable.home_mit_dll
        "iml",
        "resx",
        "wxi" -> R.drawable.home_mit_xml
        "interceptor.ts" -> R.drawable.home_mit_angular_interceptor
        "j2" -> if (light) R.drawable.home_mit_jinja_light else R.drawable.home_mit_jinja
        "jade" -> R.drawable.home_mit_pug
        "jshtm" -> R.drawable.home_mit_html
        "json" -> R.drawable.home_mit_json
        "lua" -> R.drawable.home_mit_lua
        "mcgame" -> R.drawable.home_mit_minecraft
        "mist.tsx" -> R.drawable.home_mit_mist
        "mkdn" -> R.drawable.home_mit_markdown
        "mkv" -> R.drawable.home_mit_video
        "nf" -> R.drawable.home_mit_nextflow
        "patch" -> R.drawable.home_mit_git
        "pem" -> R.drawable.home_mit_key
        "php" -> R.drawable.home_mit_php
        "plt" -> R.drawable.home_mit_gnuplot
        "potm" -> R.drawable.home_mit_powerpoint
        "proto" -> R.drawable.home_mit_proto
        "pure" -> R.drawable.home_mit_purescript
        "qmd" -> R.drawable.home_mit_quarto
        "rahit.glsl",
        "vertex.glsl" -> R.drawable.home_mit_shader
        "rbx" -> R.drawable.home_mit_ruby
        "service.ts" -> R.drawable.home_mit_angular_service
        "story.mdx",
        "story.ts" -> R.drawable.home_mit_storybook
        "sty" -> R.drawable.home_mit_sty
        "templ" -> R.drawable.home_mit_templ
        "tl" -> R.drawable.home_mit_teal
        "tlpp" -> R.drawable.home_mit_advpl_tlpp
        "toc" -> R.drawable.home_mit_toc
        "vcxproj" -> R.drawable.home_mit_visualstudio
        "viminfo" -> R.drawable.home_mit_vim
        "woff" -> R.drawable.home_mit_font
        "wsd" -> if (light) R.drawable.home_mit_uml_light else R.drawable.home_mit_uml
        "xaml" -> R.drawable.home_mit_xaml
        "yml" -> R.drawable.home_mit_yaml
        "zig" -> R.drawable.home_mit_zig
        "🔥" -> R.drawable.home_mit_mojo
        else -> 0
    }

    private fun extension9(key: String, light: Boolean): Int = when (key) {
        "apiblueprint" -> R.drawable.home_mit_apiblueprint
        "apx",
        "bpg",
        "erf",
        "gpr",
        "jfif",
        "mdp",
        "png" -> R.drawable.home_mit_image
        "arj",
        "fat",
        "swm",
        "xar",
        "zstd" -> R.drawable.home_mit_zip
        "asddls" -> R.drawable.home_mit_abap
        "axaml",
        "isml",
        "project",
        "vbproj",
        "xsd" -> R.drawable.home_mit_xml
        "catpart",
        "catproduct" -> R.drawable.home_mit_3d
        "cbx" -> R.drawable.home_mit_cbx
        "cl",
        "lsp" -> R.drawable.home_mit_lisp
        "cleanpeg" -> R.drawable.home_mit_peg
        "code-profile" -> R.drawable.home_mit_vscode
        "crx" -> R.drawable.home_mit_chrome
        "cshrc",
        "ebuild",
        "profile" -> R.drawable.home_mit_console
        "d.ts" -> R.drawable.home_mit_typescript_def
        "dot" -> R.drawable.home_mit_dotjs
        "f03" -> R.drawable.home_mit_fortran
        "geojson",
        "har" -> R.drawable.home_mit_json
        "geometry.hlsl",
        "spv",
        "tese.glsl",
        "vertexshader",
        "wgsl" -> R.drawable.home_mit_shader
        "gp" -> R.drawable.home_mit_gnuplot
        "graphql" -> R.drawable.home_mit_graphql
        "hbs" -> R.drawable.home_mit_handlebars
        "i" -> R.drawable.home_mit_c
        "iced" -> R.drawable.home_mit_coffee
        "ics" -> R.drawable.home_mit_email
        "ipp",
        "ixx" -> R.drawable.home_mit_cpp
        "js" -> R.drawable.home_mit_javascript
        "jsx.snap" -> R.drawable.home_mit_test_jsx
        "kt" -> R.drawable.home_mit_kotlin
        "less" -> R.drawable.home_mit_less
        "lol" -> R.drawable.home_mit_lolcode
        "m3u8",
        "m4p",
        "rip" -> R.drawable.home_mit_audio
        "mdtxt" -> R.drawable.home_mit_markdown
        "mermaid" -> R.drawable.home_mit_mermaid
        "nimble" -> R.drawable.home_mit_nim
        "nunjucks" -> R.drawable.home_mit_nunjucks
        "onnx" -> R.drawable.home_mit_onnx
        "pipe.js" -> R.drawable.home_mit_angular_pipe
        "plantuml" -> if (light) R.drawable.home_mit_uml_light else R.drawable.home_mit_uml
        "postgres" -> R.drawable.home_mit_database
        "pptm" -> R.drawable.home_mit_powerpoint
        "ps1xml" -> R.drawable.home_mit_powershell
        "ptm" -> R.drawable.home_mit_advpl_ptm
        "py" -> R.drawable.home_mit_python
        "rake",
        "rbi" -> R.drawable.home_mit_ruby
        "raku" -> R.drawable.home_mit_perl
        "resi" -> R.drawable.home_mit_rescript_interface
        "rmd" -> R.drawable.home_mit_r
        "spec.cjs" -> R.drawable.home_mit_test_js
        "spec.mts",
        "ts.snap" -> R.drawable.home_mit_test_ts
        "tsx" -> R.drawable.home_mit_react_ts
        "vob",
        "webm" -> R.drawable.home_mit_video
        "xlsx" -> R.drawable.home_mit_table
        "yaml-tmpreferences" -> R.drawable.home_mit_yaml
        "zeabur" -> if (light) R.drawable.home_mit_zeabur_light else R.drawable.home_mit_zeabur
        "zon" -> R.drawable.home_mit_zig
        else -> 0
    }

    private fun extension10(key: String, light: Boolean): Int = when (key) {
        "60" -> R.drawable.home_mit_slint
        "aspx" -> R.drawable.home_mit_html
        "bay",
        "data",
        "heif",
        "jb2",
        "kdc",
        "kra",
        "obm",
        "pdn",
        "pic" -> R.drawable.home_mit_image
        "bmap",
        "eot",
        "woff2" -> R.drawable.home_mit_font
        "bz2",
        "gzip",
        "lzh",
        "z" -> R.drawable.home_mit_zip
        "c++m",
        "cppm",
        "cxxm" -> R.drawable.home_mit_cpp
        "cfm",
        "lucee" -> R.drawable.home_mit_coldfusion
        "cginc",
        "frag.glsl",
        "geometry.glsl",
        "rchit.glsl",
        "tess.hlsl",
        "tessellation.hlsl" -> R.drawable.home_mit_shader
        "cjs.map" -> R.drawable.home_mit_javascript_map
        "cls" -> R.drawable.home_mit_tex
        "cm" -> R.drawable.home_mit_sml
        "cmd" -> R.drawable.home_mit_console
        "component.js" -> R.drawable.home_mit_angular_component
        "css.ts" -> R.drawable.home_mit_vanilla_extract
        "d.ets" -> R.drawable.home_mit_typescript_def
        "dfxp",
        "sami" -> R.drawable.home_mit_subtitles
        "dio",
        "drawio" -> R.drawable.home_mit_drawio
        "ditamap",
        "pubxml" -> R.drawable.home_mit_xml
        "dsql" -> R.drawable.home_mit_database
        "enc",
        "flp",
        "m4a",
        "voc" -> R.drawable.home_mit_audio
        "esx" -> R.drawable.home_mit_javascript
        "fsproj" -> R.drawable.home_mit_fsharp
        "fth" -> R.drawable.home_mit_forth
        "gemspec" -> R.drawable.home_mit_ruby
        "gvy" -> R.drawable.home_mit_groovy
        "ibc" -> R.drawable.home_mit_idris
        "ino" -> R.drawable.home_mit_arduino
        "ipa" -> R.drawable.home_mit_applescript
        "jt",
        "smt" -> R.drawable.home_mit_3d
        "kts" -> R.drawable.home_mit_kotlin
        "lisp" -> R.drawable.home_mit_lisp
        "markdoc.md" -> R.drawable.home_mit_markdoc
        "mcaddon" -> R.drawable.home_mit_minecraft
        "mist.js" -> R.drawable.home_mit_mist
        "mli" -> R.drawable.home_mit_ocaml
        "mxml" -> R.drawable.home_mit_mxml
        "nginx" -> R.drawable.home_mit_nginx
        "odin" -> R.drawable.home_mit_odin
        "openapi.yml" -> if (light) R.drawable.home_mit_openapi_light else R.drawable.home_mit_openapi
        "ppsm" -> R.drawable.home_mit_powerpoint
        "pyc" -> R.drawable.home_mit_python_misc
        "qcow" -> R.drawable.home_mit_disc
        "robot" -> R.drawable.home_mit_robot
        "route.jsx" -> R.drawable.home_mit_routing
        "sls" -> R.drawable.home_mit_salt
        "steadybit.yaml" -> R.drawable.home_mit_steadybit
        "stories.ts" -> R.drawable.home_mit_storybook
        "tag" -> R.drawable.home_mit_riot
        "template" -> R.drawable.home_mit_template
        "test.mjs" -> R.drawable.home_mit_test_js
        "tofu" -> if (light) R.drawable.home_mit_opentofu_light else R.drawable.home_mit_opentofu
        "toml" -> if (light) R.drawable.home_mit_toml_light else R.drawable.home_mit_toml
        "toon" -> R.drawable.home_mit_toon
        "tscn" -> R.drawable.home_mit_godot_assets
        "vhdl" -> R.drawable.home_mit_verilog
        "vim" -> R.drawable.home_mit_vim
        "wat" -> R.drawable.home_mit_webassembly
        "winget",
        "yaml-tmtheme" -> R.drawable.home_mit_yaml
        "workbook" -> R.drawable.home_mit_markdown
        "wrap" -> R.drawable.home_mit_meson
        "yuv" -> R.drawable.home_mit_video
        "yyz" -> R.drawable.home_mit_gamemaker
        else -> 0
    }

    private fun extension11(key: String, light: Boolean): Int = when (key) {
        "alloy" -> R.drawable.home_mit_grafana_alloy
        "asm" -> R.drawable.home_mit_assembly
        "balx" -> R.drawable.home_mit_ballerina
        "bench.tsx" -> R.drawable.home_mit_bench_jsx
        "bib" -> R.drawable.home_mit_bibliography
        "brk",
        "pbm",
        "sai",
        "x3f",
        "xcf" -> R.drawable.home_mit_image
        "cnf",
        "dlc" -> R.drawable.home_mit_settings
        "command" -> R.drawable.home_mit_command
        "controller.js" -> R.drawable.home_mit_controller
        "cshtml" -> R.drawable.home_mit_razor
        "css.mjs" -> R.drawable.home_mit_vanilla_extract
        "do" -> R.drawable.home_mit_tcl
        "ent",
        "rng",
        "wxl",
        "xml.dist.sample",
        "xmp" -> R.drawable.home_mit_xml
        "fsx" -> R.drawable.home_mit_fsharp
        "gr" -> R.drawable.home_mit_grain
        "gypi",
        "pyt",
        "rpy" -> R.drawable.home_mit_python
        "h.in",
        "hpp.in" -> R.drawable.home_mit_cpp
        "hex" -> R.drawable.home_mit_hex
        "hs" -> R.drawable.home_mit_haskell
        "html",
        "html_vm" -> R.drawable.home_mit_html
        "ibd",
        "mdb",
        "plpgsql" -> R.drawable.home_mit_database
        "it",
        "m4b",
        "m4r",
        "pkf",
        "vqf",
        "wma" -> R.drawable.home_mit_audio
        "iuml" -> if (light) R.drawable.home_mit_uml_light else R.drawable.home_mit_uml
        "jar" -> R.drawable.home_mit_jar
        "jsconfig.json" -> R.drawable.home_mit_jsconfig
        "jsonc" -> R.drawable.home_mit_json
        "jst" -> R.drawable.home_mit_dotjs
        "k" -> R.drawable.home_mit_kcl
        "kv" -> R.drawable.home_mit_kivy
        "lock" -> R.drawable.home_mit_lock
        "markdoc",
        "mdoc" -> R.drawable.home_mit_markdoc
        "mca",
        "mcmeta",
        "mus" -> R.drawable.home_mit_minecraft
        "module.js" -> R.drawable.home_mit_angular
        "mqo",
        "pmx",
        "stl",
        "x_b" -> R.drawable.home_mit_3d
        "odp" -> R.drawable.home_mit_powerpoint
        "pal" -> R.drawable.home_mit_palette
        "pegjs" -> R.drawable.home_mit_peg
        "phtml" -> R.drawable.home_mit_php
        "rego" -> R.drawable.home_mit_opa
        "rjs" -> R.drawable.home_mit_ruby
        "rkt" -> R.drawable.home_mit_racket
        "rm" -> R.drawable.home_mit_video
        "routes.jsx" -> R.drawable.home_mit_routing
        "spec-d.ts",
        "test.ts" -> R.drawable.home_mit_test_ts
        "stories.vue" -> R.drawable.home_mit_storybook
        "svelte" -> R.drawable.home_mit_svelte
        "swagger.yaml" -> R.drawable.home_mit_swagger
        "taskfile.yml" -> R.drawable.home_mit_taskfile
        "tesc.glsl",
        "vert.glsl",
        "vsh" -> R.drawable.home_mit_shader
        "tnef" -> R.drawable.home_mit_email
        "typ" -> R.drawable.home_mit_typst
        "vbox-prev",
        "vdi" -> R.drawable.home_mit_virtual
        "vimrc" -> R.drawable.home_mit_vim
        "vsixmanifest" -> R.drawable.home_mit_vscode
        "whl" -> R.drawable.home_mit_python_misc
        "wim",
        "zst" -> R.drawable.home_mit_zip
        "zsh-theme" -> R.drawable.home_mit_console
        else -> 0
    }

    private fun extension12(key: String, light: Boolean): Int = when (key) {
        "3dm",
        "3mf",
        "fbx",
        "iges",
        "step" -> R.drawable.home_mit_3d
        "ait" -> if (light) R.drawable.home_mit_adobe_illustrator_light else R.drawable.home_mit_adobe_illustrator
        "amx" -> R.drawable.home_mit_pawn
        "apfs",
        "squashfs",
        "tbz" -> R.drawable.home_mit_zip
        "apk" -> R.drawable.home_mit_android
        "bak",
        "dblite",
        "sqlite" -> R.drawable.home_mit_database
        "bbl" -> R.drawable.home_mit_bibliography
        "blink" -> if (light) R.drawable.home_mit_blink_light else R.drawable.home_mit_blink
        "bubble" -> R.drawable.home_mit_dinophp
        "cjs" -> R.drawable.home_mit_javascript
        "cjs.snap",
        "spec.js" -> R.drawable.home_mit_test_js
        "coffee" -> R.drawable.home_mit_coffee
        "comp.hlsl",
        "computeshader",
        "fragment.glsl",
        "geom",
        "hlsli",
        "rcall.glsl" -> R.drawable.home_mit_shader
        "compose.yml",
        "dockerfile" -> R.drawable.home_mit_docker
        "cpy" -> R.drawable.home_mit_python
        "csl",
        "csproj.user",
        "tld" -> R.drawable.home_mit_xml
        "eip",
        "jbig2",
        "mdc",
        "webp" -> R.drawable.home_mit_image
        "eyaml",
        "yml.dist" -> R.drawable.home_mit_yaml
        "fast" -> R.drawable.home_mit_lisp
        "fnt" -> R.drawable.home_mit_font
        "fsi" -> R.drawable.home_mit_fsharp
        "gdshaderinc",
        "tres" -> R.drawable.home_mit_godot_assets
        "gleam" -> R.drawable.home_mit_gleam
        "gs" -> R.drawable.home_mit_apps_script
        "jinja2" -> if (light) R.drawable.home_mit_jinja_light else R.drawable.home_mit_jinja
        "leex" -> R.drawable.home_mit_elixir
        "liquid" -> R.drawable.home_mit_liquid
        "mcr",
        "mcworld" -> R.drawable.home_mit_minecraft
        "mi" -> R.drawable.home_mit_c
        "mjml" -> R.drawable.home_mit_mjml
        "mts" -> R.drawable.home_mit_typescript
        "mts.snap" -> R.drawable.home_mit_test_ts
        "nginxconfig" -> R.drawable.home_mit_nginx
        "npmrc" -> R.drawable.home_mit_settings
        "nsa",
        "wav" -> R.drawable.home_mit_audio
        "otne" -> R.drawable.home_mit_otne
        "p7s" -> R.drawable.home_mit_email
        "php4" -> R.drawable.home_mit_php
        "pl" -> R.drawable.home_mit_prolog
        "ppam" -> R.drawable.home_mit_powerpoint
        "riot" -> R.drawable.home_mit_riot
        "sketch" -> R.drawable.home_mit_sketch
        "so" -> R.drawable.home_mit_dll
        "spec.jsx" -> R.drawable.home_mit_test_jsx
        "svelte.js" -> R.drawable.home_mit_svelte_js
        "swagger.json" -> R.drawable.home_mit_swagger
        "swiftsourceinfo" -> R.drawable.home_mit_swift
        "synctex" -> R.drawable.home_mit_tex
        "tosd" -> if (light) R.drawable.home_mit_toml_light else R.drawable.home_mit_toml
        "ua" -> R.drawable.home_mit_uiua
        "vb" -> R.drawable.home_mit_visualstudio
        "veda" -> R.drawable.home_mit_vedic
        "vfl" -> R.drawable.home_mit_vfl
        "vscodeignore",
        "vsix" -> R.drawable.home_mit_vscode
        else -> 0
    }

    private fun extension13(key: String, light: Boolean): Int = when (key) {
        ".ncurc.yml" -> R.drawable.home_mit_dependencies_update
        "8svx",
        "alac",
        "mscz",
        "musx",
        "weba" -> R.drawable.home_mit_audio
        "a51",
        "aea",
        "agc",
        "ags",
        "nasm" -> R.drawable.home_mit_assembly
        "adp",
        "myi",
        "odb" -> R.drawable.home_mit_database
        "afphoto",
        "ami",
        "dng",
        "ppm",
        "rw2" -> R.drawable.home_mit_image
        "azure-pipelines-main.yml",
        "azure-pipelines.yml" -> R.drawable.home_mit_azure_pipelines
        "bal" -> R.drawable.home_mit_ballerina
        "bench.ts" -> R.drawable.home_mit_bench_ts
        "blg" -> R.drawable.home_mit_bibliography
        "cbl" -> R.drawable.home_mit_cobol
        "ccm",
        "cp",
        "mii" -> R.drawable.home_mit_cpp
        "comp.glsl",
        "hlsl",
        "rint",
        "ts.glsl" -> R.drawable.home_mit_shader
        "containerfile" -> R.drawable.home_mit_docker
        "csproj",
        "suo" -> R.drawable.home_mit_visualstudio
        "dhall" -> R.drawable.home_mit_dhall
        "directory" -> R.drawable.home_mit_settings
        "e2e-spec.ts" -> R.drawable.home_mit_test_ts
        "edge" -> R.drawable.home_mit_edge
        "env" -> R.drawable.home_mit_tune
        "excalidraw.png" -> R.drawable.home_mit_excalidraw
        "exp",
        "xsh" -> R.drawable.home_mit_console
        "f90" -> R.drawable.home_mit_fortran
        "features" -> R.drawable.home_mit_cucumber
        "fs" -> R.drawable.home_mit_fsharp
        "g4" -> R.drawable.home_mit_antlr
        "gd" -> R.drawable.home_mit_godot
        "glb",
        "ipt",
        "mesh",
        "ply",
        "x_t" -> R.drawable.home_mit_3d
        "huff" -> if (light) R.drawable.home_mit_huff_light else R.drawable.home_mit_huff
        "inky.php" -> R.drawable.home_mit_laravel
        "json5",
        "ts.map",
        "tsbuildinfo" -> R.drawable.home_mit_json
        "liz",
        "taz",
        "tzstd" -> R.drawable.home_mit_zip
        "lrc" -> R.drawable.home_mit_lyric
        "luau" -> R.drawable.home_mit_luau
        "ly" -> R.drawable.home_mit_lilypond
        "m" -> R.drawable.home_mit_objective_c
        "mcstructure" -> R.drawable.home_mit_minecraft
        "nix" -> R.drawable.home_mit_nix
        "oft" -> R.drawable.home_mit_email
        "opam" -> R.drawable.home_mit_opam
        "php5" -> R.drawable.home_mit_php
        "pm" -> R.drawable.home_mit_perl
        "pnml" -> R.drawable.home_mit_coloredpetrinets
        "pro" -> R.drawable.home_mit_prolog
        "proj",
        "xml.dist" -> R.drawable.home_mit_xml
        "pub" -> R.drawable.home_mit_key
        "rhtml" -> R.drawable.home_mit_html
        "ronn" -> R.drawable.home_mit_markdown
        "routes.js" -> R.drawable.home_mit_routing
        "rql" -> R.drawable.home_mit_restql
        "scm" -> R.drawable.home_mit_scheme
        "slim" -> R.drawable.home_mit_slim
        "spec-d.tsx",
        "test.tsx" -> R.drawable.home_mit_test_jsx
        "sublime-workspace" -> R.drawable.home_mit_sublime
        "suit" -> R.drawable.home_mit_font
        "tcl" -> R.drawable.home_mit_tcl
        "vagrantfile" -> R.drawable.home_mit_vagrant
        "yaml.dist" -> R.drawable.home_mit_yaml
        else -> 0
    }

    private fun extension14(key: String, light: Boolean): Int = when (key) {
        "ada",
        "ali" -> R.drawable.home_mit_ada
        "aif",
        "qcp" -> R.drawable.home_mit_audio
        "asp",
        "shtml" -> R.drawable.home_mit_html
        "bash_login",
        "bash_profile",
        "xsession",
        "yashrc",
        "zshenv" -> R.drawable.home_mit_console
        "bench.cjs" -> R.drawable.home_mit_bench_js
        "bench.mts" -> R.drawable.home_mit_bench_ts
        "cmx" -> R.drawable.home_mit_ocaml
        "css.tsx" -> R.drawable.home_mit_vanilla_extract
        "db",
        "ldf",
        "mde",
        "pdb",
        "psql",
        "sql" -> R.drawable.home_mit_database
        "directive.ts" -> R.drawable.home_mit_angular_directive
        "djt" -> R.drawable.home_mit_django
        "dmg",
        "vmdk" -> R.drawable.home_mit_disc
        "e2e-spec.cjs" -> R.drawable.home_mit_test_js
        "e2e-spec.mts" -> R.drawable.home_mit_test_ts
        "ejs" -> R.drawable.home_mit_ejs
        "f08" -> R.drawable.home_mit_fortran
        "fpx",
        "jxl",
        "k25",
        "mef",
        "ora",
        "tga" -> R.drawable.home_mit_image
        "ftl" -> R.drawable.home_mit_freemarker
        "fxp" -> R.drawable.home_mit_foxpro
        "glsl",
        "rgen",
        "rgen.glsl",
        "rmiss",
        "rmiss.glsl" -> R.drawable.home_mit_shader
        "gnu" -> R.drawable.home_mit_gnuplot
        "gpg",
        "passwd" -> R.drawable.home_mit_key
        "groovy" -> R.drawable.home_mit_groovy
        "gyp",
        "pyw" -> R.drawable.home_mit_python
        "horusec-config.json" -> R.drawable.home_mit_horusec
        "inc" -> R.drawable.home_mit_assembly
        "ins" -> R.drawable.home_mit_doctex_installer
        "lang" -> R.drawable.home_mit_i18n
        "lz",
        "tpz" -> R.drawable.home_mit_zip
        "mbox" -> R.drawable.home_mit_email
        "mcfunction",
        "mclevel" -> R.drawable.home_mit_minecraft
        "mdwn" -> R.drawable.home_mit_markdown
        "mist.jsx" -> R.drawable.home_mit_mist
        "mk" -> R.drawable.home_mit_makefile
        "mrpack" -> R.drawable.home_mit_mrpack
        "ndjson" -> R.drawable.home_mit_json
        "ndst.yaml" -> R.drawable.home_mit_ndst
        "ng-template" -> R.drawable.home_mit_angular
        "nginxconf" -> R.drawable.home_mit_nginx
        "ods",
        "xlsm" -> R.drawable.home_mit_table
        "ogv" -> R.drawable.home_mit_video
        "plist" -> R.drawable.home_mit_xml
        "ps1" -> R.drawable.home_mit_powershell
        "raml" -> R.drawable.home_mit_raml
        "razor" -> R.drawable.home_mit_razor
        "route.js" -> R.drawable.home_mit_routing
        "sass" -> R.drawable.home_mit_sass
        "slddrw" -> R.drawable.home_mit_3d
        "snakemake" -> R.drawable.home_mit_snakemake
        "stan" -> R.drawable.home_mit_stan
        "stories.tsx" -> R.drawable.home_mit_storybook
        "tfvars" -> R.drawable.home_mit_terraform
        "tree" -> R.drawable.home_mit_tree
        "vtl" -> R.drawable.home_mit_velocity
        "wls" -> R.drawable.home_mit_wolframlanguage
        else -> 0
    }

    private fun extension15(key: String, light: Boolean): Int = when (key) {
        "3fr",
        "exr",
        "icns",
        "ktx",
        "nef",
        "pgf",
        "pnm",
        "reb" -> R.drawable.home_mit_image
        "accdb",
        "kdbx",
        "mdf" -> R.drawable.home_mit_database
        "adb" -> R.drawable.home_mit_ada
        "astro" -> R.drawable.home_mit_astro
        "au3" -> R.drawable.home_mit_autoit
        "beancount" -> R.drawable.home_mit_beancount
        "bpmn",
        "dtml",
        "jrxml",
        "manifest",
        "menu",
        "pubxml.user",
        "tmx",
        "xul" -> R.drawable.home_mit_xml
        "cabal" -> R.drawable.home_mit_cabal
        "comp",
        "fs.glsl",
        "geom.hlsl",
        "rint.glsl",
        "vs.glsl" -> R.drawable.home_mit_shader
        "cr" -> if (light) R.drawable.home_mit_crystal_light else R.drawable.home_mit_crystal
        "cson" -> R.drawable.home_mit_coffee
        "ctp" -> R.drawable.home_mit_php
        "dart" -> R.drawable.home_mit_dart
        "docker-compose.yml" -> R.drawable.home_mit_docker
        "edn" -> R.drawable.home_mit_clojure
        "erl" -> R.drawable.home_mit_erlang
        "fen" -> if (light) R.drawable.home_mit_chess_light else R.drawable.home_mit_chess
        "filter.js",
        "pipe.ts" -> R.drawable.home_mit_angular_pipe
        "font" -> R.drawable.home_mit_font
        "fun",
        "lex" -> R.drawable.home_mit_sml
        "garden.yml" -> R.drawable.home_mit_garden
        "gnuplot" -> R.drawable.home_mit_gnuplot
        "hip" -> R.drawable.home_mit_hip
        "html.bubble" -> R.drawable.home_mit_dinophp
        "jav" -> R.drawable.home_mit_java
        "js.snap" -> R.drawable.home_mit_test_js
        "jsx" -> R.drawable.home_mit_react
        "key",
        "sha256sums",
        "shasum" -> R.drawable.home_mit_key
        "lottie" -> R.drawable.home_mit_lottie
        "m3u",
        "opus",
        "ra",
        "sf2",
        "wv" -> R.drawable.home_mit_audio
        "m4v",
        "mp2",
        "ogg" -> R.drawable.home_mit_video
        "markdown",
        "mdown" -> R.drawable.home_mit_markdown
        "mcpack" -> R.drawable.home_mit_minecraft
        "ml" -> R.drawable.home_mit_ocaml
        "ndst.json" -> R.drawable.home_mit_ndst
        "njk" -> R.drawable.home_mit_nunjucks
        "o",
        "stp",
        "vox" -> R.drawable.home_mit_3d
        "odt" -> R.drawable.home_mit_word
        "pdm.toml" -> R.drawable.home_mit_pdm
        "po" -> R.drawable.home_mit_i18n
        "psb" -> if (light) R.drawable.home_mit_adobe_photoshop_light else R.drawable.home_mit_adobe_photoshop
        "pwf" -> R.drawable.home_mit_pytorch
        "restql" -> R.drawable.home_mit_restql
        "rpm" -> R.drawable.home_mit_zip
        "sast" -> R.drawable.home_mit_sas
        "slx" -> R.drawable.home_mit_simulink
        "smi",
        "ttml" -> R.drawable.home_mit_subtitles
        "spec.cts" -> R.drawable.home_mit_test_ts
        "swc" -> R.drawable.home_mit_adobe_swc
        "swiftmodule" -> R.drawable.home_mit_swift
        "tauri" -> R.drawable.home_mit_tauri
        "tfstate" -> R.drawable.home_mit_terraform
        "ts" -> R.drawable.home_mit_typescript
        "tsx.snap" -> R.drawable.home_mit_test_jsx
        "url" -> R.drawable.home_mit_url
        "vcl" -> R.drawable.home_mit_varnish
        "vedic" -> R.drawable.home_mit_vedic
        "xls" -> R.drawable.home_mit_table
        "xsessionrc",
        "zlogin",
        "zprofile",
        "zsh" -> R.drawable.home_mit_console
        else -> 0
    }
}
