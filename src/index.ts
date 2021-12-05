import fs from 'fs';
import execa from 'execa';
import { join } from 'path';
import {
	BuildOptions,
	download,
	createLambda,
	shouldServe,
	FileFsRef,
} from '@vercel/build-utils';

// `chmod()` is required for usage with `vercel-dev-runtime`
// since file mode is not preserved in Vercel deployments.
fs.chmodSync(join(__dirname, 'install-bb.sh'), 0o755);

export const version = 3;

export { shouldServe };

export async function build({
	workPath,
	files,
	entrypoint,
	meta = {},
	config = {}
}: BuildOptions) {
	await download(files, workPath, meta);

	const cachePath = join(workPath, '.vercel', 'vercel-babashka-cache');
	await execa('mkdir', ['-p', cachePath]);
	await execa(join(__dirname, 'install-bb.sh'), [cachePath], {
		cwd: workPath,
		stdout: process.stdout,
		stderr: process.stderr
	});
	const bbPath = join(cachePath, 'bb');

	let nsname: string;
	try {
		const { stdout } = await execa(
			bbPath, [join(__dirname, 'get_ns.clj'), join(workPath, entrypoint)],
			{ cwd: workPath }
		);
		nsname = stdout.trim();
	} catch (error) {
		throw new Error(`Unable to determine namespace of entrypoint ${entrypoint}: ${error}`);
	}

	const runtimeToolsPath = join(__dirname, 'runtime');
	files = {
		...files,
		'bootstrap': new FileFsRef({
			fsPath: join(runtimeToolsPath, 'bootstrap'),
			mode: 0o755
		}),
		'lambda_invoker.clj': files['lambda_invoker.clj'] || (new FileFsRef({
			fsPath: join(runtimeToolsPath, 'lambda_invoker.clj'),
		})),
		'bb.edn': files['bb.edn'] || (new FileFsRef({
			fsPath: join(runtimeToolsPath, 'bb.default.edn'),
		})),
		'bb': new FileFsRef({
			fsPath: bbPath,
			mode: 0o755
		})
	};

	const configEnv = config.import?.env || {};
	const output = await createLambda({
		files,
		handler: 'not-needed-for-the-provided-runtime',
		runtime: 'provided',
		environment: {
			...configEnv,
			ENTRY_NS: nsname
		}
	});

	return {
		output,
		watch: [
			'src/**',
			'api/**'
		]
	};
}
