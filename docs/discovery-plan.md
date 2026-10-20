# Repository discovery and measurement

## Search intent

| Reader's question | Relevant entry point |
|---|---|
| Java thread pool starvation / ExecutorService Future.get hangs | Repository description, topics, README quick start and FAQ |
| Java thread dump analysis / JFR troubleshooting | Description, README evidence section and scenario walkthrough |
| Java 스레드 풀 고갈 / JVM 장애 분석 | Korean README and bilingual dashboard/reports |

GitHub's default repository search covers names, descriptions and topics. README text is searchable with `in:readme`; improving it also helps readers decide whether to try the lab. These changes do not establish a search ranking or guarantee stars. See [GitHub repository search documentation](https://docs.github.com/en/search-github/searching-on-github/searching-for-repositories).

Keep topics specific to implemented behavior. The repository currently implements one thread pool starvation scenario; do not describe planned scenarios as available features. See [GitHub topics documentation](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/classifying-your-repository-with-topics).

## Share preview

`docs/media/social-preview.png` is a 1280×640 card showing the controlled failure/fix outcome. Rebuild with `python3 scripts/build_social_preview.py` using the optional Pillow tooling described in [media instructions](media/README.md). Upload it through repository Settings → Social preview → Edit. Committing the image alone does not configure GitHub's social preview. See [GitHub social preview documentation](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/customizing-your-repositorys-social-media-preview).

## Measure results

A private local baseline was captured before the metadata update on October 16, 2026 (KST): 0 stars, 0 forks, and 0 reported views/clones in GitHub's available rolling traffic window. These are initial observations, not evidence of effectiveness.

After 7 and 14 days, manually compare stars, forks, unique visitors, unique cloners and actionable first-run feedback. Record the capture date and the traffic window; overlapping rolling windows cannot be added together. Visits include maintainer and automated activity and do not identify search-only acquisition. A visit-to-star ratio is only a rough aggregate, not a tracked conversion funnel.

Prioritize successful first runs and useful feedback alongside stars. Share the actual runnable example and evidence when appropriate; do not use unrelated keywords, fabricated endorsements, or automated promotional messages.
