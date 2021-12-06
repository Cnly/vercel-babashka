[<img src="https://og-image.vercel.app/**vercel-babashka**.png?theme=light&md=1&fontSize=100px&images=https%3A%2F%2Fassets.vercel.com%2Fimage%2Fupload%2Ffront%2Fassets%2Fdesign%2Fvercel-triangle-black.svg&images=https://github.com/babashka/babashka/raw/master/logo/icon.svg&widths=184&widths=230&heights=160&heights=230">](https://github.com/cnly/vercel-babashka)

<p align="center">
Deploy your [Babashka](https://github.com/babashka/babashka) scripts as Vercel Serverless Functions.
</p>

## Usage

*Note: You don't need to clone the repository to use this runtime.*

This section assumes you've had some experience with Vercel Serverless
Functions and the [officially supported
runtimes](https://vercel.com/docs/concepts/functions/supported-languages).

Similar to how you'll do for the officially supported languages, you can put
your functions under `api/` (other directories *should* also work; just modify
your `vercel.json` and `bb.edn` accordingly, see below).
Babashka scripts within the `api/` directory containing a public `handler`
function will be served as Serverless Functions.
For example, the following will live in `api/hello.clj`:

```clj
(ns hello)

(defn handler [event]
  (format "Hello from babashka %s!" (System/getProperty "babashka.version")))
```

To use this custom runtime, you'll also need the following `vercel.json`:

```json
{
  "functions": {
    "api/**/*.clj": {
      "runtime": "vercel-babashka@0.1.1"
    }
  }
}
```

You can learn more about community runtimes
[here](https://vercel.com/docs/runtimes#advanced-usage/community-runtimes), and
`vercel.json` options
[here](https://vercel.com/docs/cli#project-configuration).

Example deployment: https://vercel-babashka.vercel.app/api/hello

Other examples can be found under the [api](/api) directory.

### Handler Function

You've seen in the about example that the `handler` function is called with an
`event` argument and returns a response.
In this section we'll see more details about the argument and return value.

While in AWS Lambda where there're different types of events coming from
different event sources, in Vercel Serverless Functions there doesn't seem to
be event sources other than HTTP requests, so the `event` here will probably
always look like an HTTP request from the client.

You can try this [example](https://vercel-babashka.vercel.app/api/echo-pretty)
to see how the `event` will look like.
Basically, it is a map with the following keys:

* `:method`: The HTTP method of the request. Example: `"GET"`, `"POST"`.
* `:host`: The host of the request. Example: `"vercel-babashka.vercel.app"`.
* `:path`: The path of the request. Example: `"/api/hello?q=test"`.
* `:headers`: A map of the headers of the request where keys are converted to
  keywords like `:content-type`.
* `:encoding`: A string describing the encoding of the request body. Example:
  `"base64"`. Note:
  * This runtime automatically decodes base64-encoded bodies and also sets
	`:encoding` to `"base64-decoded"` if that's done.
  * However, if the request is a `multipart/form-data` one and is
	base64-encoded, the body will be decoded into the `:decoded-body-bytes`
	field instead, and `:body` and `:encoding` will be untouched.
* `:body`: A string containing the body of the user request.
* `:params`: A map populated from the query string of the request, with field
  names converted to keywords. `nil` if no query parameters supplied. Example:
  `{:q "test"}`.
* `:form-params`: Like `:params`, but populated from the body of an
  `application/x-www-form-urlencoded` request.

The handler can either return a map or, as a simpler form, values of other
arbitrary types.

If a map is returned, it should contain zero or more of the following keys:

* `:status`: The HTTP status code to return. Default: `200`.
* `:headers`: A map of the headers to return. Example: `{:content-type
  "text/plain"}`. Keywords as values would also work.
* `:body`: The response body.
  * To respond with binary data, return a byte array as the body.
  * Will be converted to a string with `clojure.core/str` if it's not already
	and not bytes.
* `:jsonify`: If true, the body is converted to a string by calling
  `json/encode` on it, in contrast to using `str` above. Useful for creating
  JSON responses.

A note on the content type header: If you don't specify one, the runtime will
make a guess:

* If `:jsonify` is true, the content type will be `"application/json"`.
* If `:body` is a byte array, the content type will be
  `"application/octet-stream"`.
* Otherwise, the content type will be `"text/plain"`.

If anything other than a map is returned from the handler, it will be treated
as a map `{:body it}` and the above will apply.

### `bb.edn`

You can optionally put a `bb.edn` file in the root of your project.
If you don't, a default one will be used, which is just `{:paths ["api"]}`.

### Rewriting Request Paths

The path to your handler may be in `snake_case` due to Clojure limitations but
you may want users to call the APIs in `kebab-case`.
You can use
[rewrites](https://vercel.com/support/articles/can-i-redirect-from-a-subdomain-to-a-subpath)
in your `vercel.json` to do that.
A more detailed explanation for the options is available in the Next.js
[docs](https://nextjs.org/docs/api-reference/next.config.js/rewrites).

For an example, take a look at this project's [`vercel.json`](/vercel.json),
which contains the following to allow users to access any APIs in
`api/any_thing` using `/api/any-thing`:

```json
{
  "rewrites": [
    {
      "source": "/api/:p1(.+)-:p2(.+)/:path*",
      "destination": "/api/:p1*_:p2*/:path*"
    }
  ]
}
```

### Environment Variables

You can specify environment variables from the project settings in your
browser. Currently supported environment variables are:

* `BABASHKA_INSTALL_VERSION`: By default, the runtime doesn't care about the
  version of bb (if you're developing locally and have it installed in your
  system), and when deployed, the installer will install the latest version. If
  you want a specific version, set this variable. Example: `0.6.7`.

## Local Development

To do development work on this project, simply clone it, run `yarn`, and then
`vercel dev`.

To use an unpublished version of this project as a custom runtime in some other
project, first do `npm link` in this project, and in the other project, use
`/path/to/this/project/tools/fix_vercel_npm_install.clj vercel dev` instead of
`vercel dev`.

## Acknowledgements

This runtime is heavily inspired by
[dainiusjocas/babashka-lambda](https://github.com/dainiusjocas/babashka-lambda)
and [importpw/vercel-bash](https://github.com/importpw/vercel-bash).