declare module 'virtual:testops-contracts' {
  const validators: Record<string, (value: unknown) => boolean>;
  export default validators;
}
