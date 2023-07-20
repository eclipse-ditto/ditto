import {argv} from 'node:process';
import * as esbuild from 'esbuild';
import {sassPlugin} from 'esbuild-sass-plugin';

const config = {
  entryPoints: ['main.ts'],
  bundle: true,
  outdir: 'dist',
  loader: {
    '.html': 'text',
  },
  plugins: [sassPlugin()],
};

if (argv[2] === 'serve') {
  config.sourcemap = true;

  const ctx = await esbuild.context(config);

  let {host, port} = await ctx.serve({
    servedir: '.',
  });
} else {
  config.minify = true;

  await esbuild.build(config);
}


