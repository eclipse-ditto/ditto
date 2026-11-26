declare module "*.html" {
  const content: string;
  export default content;
}

// side-effect style imports, bundled by esbuild (esbuild-sass-plugin / css loader);
// TypeScript 6 checks side-effect imports for module resolution (TS2882)
declare module "*.css";
declare module "*.scss";
declare module "ace-diff/styles";
