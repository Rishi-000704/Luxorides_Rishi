package com.core.controllers;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Welcome {

	@GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
	public String getMessage() {
		return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Fleetovo Core API</title>

                    <style>
                        * {
                            box-sizing: border-box;
                        }

                        body {
                            min-height: 100vh;
                            margin: 0;
                            display: grid;
                            place-items: center;
                            padding: 24px;
                            background: #0d1117;
                            color: #c9d1d9;
                            font-family: ui-monospace, SFMono-Regular, Menlo,
                                         Monaco, Consolas, monospace;
                        }

                        main {
                            width: 100%;
                            max-width: 560px;
                            padding: 28px;
                            border: 1px solid #30363d;
                            border-radius: 8px;
                            background: #161b22;
                        }

                        header {
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            gap: 16px;
                            padding-bottom: 18px;
                            border-bottom: 1px solid #30363d;
                        }

                        h1 {
                            margin: 0;
                            color: #f0f6fc;
                            font-size: 18px;
                            font-weight: 600;
                        }

                        .status {
                            display: inline-flex;
                            align-items: center;
                            gap: 8px;
                            color: #3fb950;
                            font-size: 13px;
                        }

                        .dot {
                            width: 8px;
                            height: 8px;
                            border-radius: 50%;
                            background: #3fb950;
                        }

                        section {
                            padding-top: 20px;
                        }

                        p {
                            margin: 0 0 18px;
                            color: #8b949e;
                            font-size: 14px;
                            line-height: 1.6;
                        }

                        dl {
                            display: grid;
                            grid-template-columns: 110px 1fr;
                            gap: 10px 16px;
                            margin: 0;
                            font-size: 13px;
                        }

                        dt {
                            color: #8b949e;
                        }

                        dd {
                            margin: 0;
                            color: #c9d1d9;
                        }
                    </style>
                </head>

                <body>
                    <main>
                        <header>
                            <h1>Fleetovo Core API</h1>

                            <span class="status">
                                <span class="dot"></span>
                                operational
                            </span>
                        </header>

                        <section>
                            <p>
                                Backend service is running and accepting requests.
                            </p>

                            <dl>
                                <dt>service</dt>
                                <dd>fleetovo-core</dd>

                                <dt>status</dt>
                                <dd>UP</dd>

                                <dt>environment</dt>
                                <dd>production</dd>
                            </dl>
                        </section>
                    </main>
                </body>
                </html>
                """;
	}
}