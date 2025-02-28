import re
import os

def parse_log_file(log_file):
    violations = []
    excluded_items = {
        'classes': [],
        'methods': [],
        'fields': []
    }
    ignored_static_inits = []
    current_violation = None
    current_type = None
    
    with open(log_file, 'r') as f:
        for line in f:
            if 'Invalid read:' in line:
                match = re.search(r'CORE thread \'(.*?)\' attempting to read variable (.*?) that was written by NON_CORE thread \'(.*?)\'', line)
                if match:
                    if current_violation:
                        violations.append(current_violation)
                    current_violation = {
                        'variable': match.group(2),
                        'core_thread': {
                            'thread_name': match.group(1),
                            'stack': []
                        },
                        'non_core_thread': {
                            'thread_name': match.group(3),
                            'stack': []
                        }
                    }
            elif 'Current thread stack trace:' in line:
                current_type = 'core'
            elif 'Previous NON_CORE thread write stack trace:' in line:
                current_type = 'non_core'
            elif line.strip().startswith('at ') and current_violation and current_type:
                match = re.match(r'\s*at ([\w\.]+)\.([\w<>]+)\((.*?):(\d+)\)', line)
                if match:
                    current_violation[f'{current_type}_thread']['stack'].append({
                        'class': match.group(1),
                        'method': match.group(2),
                        'file': match.group(3),
                        'line': match.group(4)
                    })
            elif 'will ignore static initialization writes' in line:
                match = re.search(r'Field (.*?) in class (.*?) will ignore static initialization writes', line)
                if match:
                    ignored_static_inits.append({
                        'field': match.group(1),
                        'class': match.group(2)
                    })
            elif 'excluded from thread safety check due to @RsmThreadSafe' in line:
                if 'Class' in line:
                    match = re.search(r'Class (.*?) is excluded', line)
                    if match:
                        excluded_items['classes'].append(match.group(1))
                elif 'Method' in line:
                    match = re.search(r'Method (.*?) in class (.*?) is excluded', line)
                    if match:
                        excluded_items['methods'].append({
                            'method': match.group(1),
                            'class': match.group(2)
                        })
                elif 'Field' in line:
                    match = re.search(r'Field (.*?) in class (.*?) is excluded', line)
                    if match:
                        excluded_items['fields'].append({
                            'field': match.group(1),
                            'class': match.group(2)
                        })
    
    if current_violation:
        violations.append(current_violation)
    return violations, excluded_items, ignored_static_inits

def escape_html(text):
    """转义HTML特殊字符"""
    return text.replace('<', '&lt;').replace('>', '&gt;')

def generate_html(violations, excluded_items, ignored_static_inits, output_file):
    html = '''
    <!DOCTYPE html>
    <html>
    <head>
        <title>Thread Safety Violations Report</title>
        <style>
            body { font-family: Arial, sans-serif; margin: 20px; }
            .violation { 
                border: 1px solid #ddd; 
                margin: 10px 0; 
                padding: 15px;
                border-radius: 5px;
            }
            .thread-info {
                margin: 10px 0;
                padding: 10px;
                background-color: #f5f5f5;
                border-radius: 3px;
            }
            .stack-trace {
                margin-left: 20px;
                font-family: monospace;
                display: none;  /* 默认隐藏 */
            }
            .variable {
                font-weight: bold;
                color: #d63031;
            }
            .thread-name {
                color: #0984e3;
                font-weight: bold;
                cursor: pointer;  /* 添加手型光标 */
                user-select: none;  /* 防止文字被选中 */
            }
            .thread-name:before {
                content: "▶";  /* 添加折叠指示箭头 */
                margin-right: 5px;
                display: inline-block;
                transition: transform 0.2s;
            }
            .thread-name.expanded:before {
                transform: rotate(90deg);  /* 展开时旋转箭头 */
            }
            .location {
                color: #00b894;
            }
            h1 { color: #2d3436; }
            .summary {
                margin: 20px 0;
                padding: 10px;
                background-color: #dfe6e9;
                border-radius: 5px;
            }
            .excluded-section {
                margin: 20px 0;
                padding: 15px;
                background-color: #81ecec;
                border-radius: 5px;
            }
            .excluded-title {
                color: #00b894;
                font-weight: bold;
                margin-bottom: 10px;
            }
            .excluded-item {
                margin: 5px 0;
                padding: 5px;
                background-color: #fff;
                border-radius: 3px;
            }
            .ignored-section {
                margin: 10px 0;  /* 减小边距使其看起来像子区域 */
                padding: 15px;
                background-color: #74b9ff;
                border-radius: 5px;
            }
            .ignored-title {
                color: #0984e3;
                font-weight: bold;
                margin-bottom: 10px;
            }
            .ignored-item {
                margin: 5px 0;
                padding: 5px;
                background-color: #fff;
                border-radius: 3px;
            }
        </style>
        <script>
            function toggleStack(element) {
                const stackTrace = element.nextElementSibling;
                const isExpanded = element.classList.toggle('expanded');
                stackTrace.style.display = isExpanded ? 'block' : 'none';
            }
        </script>
    </head>
    <body>
        <h1>Thread Safety Violations Report</h1>
        
        <div class="excluded-section">
            <h3>Items Excluded by @RsmThreadSafe</h3>
            
            <div class="excluded-title">Excluded Classes:</div>
    '''
    
    for class_name in excluded_items['classes']:
        html += f'''
            <div class="excluded-item">{class_name}</div>
        '''
    
    html += '''
            <div class="excluded-title">Excluded Methods:</div>
    '''
    
    for method in excluded_items['methods']:
        html += f'''
            <div class="excluded-item">{method['class']}.{method['method']}</div>
        '''
    
    html += '''
            <div class="excluded-title">Excluded Fields:</div>
    '''
    
    for field in excluded_items['fields']:
        html += f'''
            <div class="excluded-item">{field['class']}.{field['field']}</div>
        '''
    
    html += '''
        </div>
        
        <div class="ignored-section">
            <h3>Items Excluded by @IgnoreStaticInit</h3>
            <div class="ignored-title">Fields ignoring static initialization writes:</div>
    '''
    
    for item in ignored_static_inits:
        html += f'''
            <div class="ignored-item">{item['class']}.{item['field']}</div>
        '''
    
    html += '''
        </div>
        
        <div class="summary">
            <h3>Violations Summary</h3>
            <p>Total violations found: ''' + str(len(violations)) + '''</p>
        </div>
    '''
    
    for violation in violations:
        html += f'''
        <div class="violation">
            <div class="variable">Variable: {violation['variable']}</div>
            
            <div class="thread-info">
                <div class="thread-name" onclick="toggleStack(this)">RSM Thread: {violation['core_thread']['thread_name']}</div>
                <div class="stack-trace">
        '''
        
        for frame in violation['core_thread']['stack']:
            method_name = escape_html(frame['method'])
            html += f'''
                    <div class="location">at {frame['class']}.{method_name}({frame['file']}:line {frame['line']})</div>
            '''
            
        html += f'''
                </div>
            </div>
            
            <div class="thread-info">
                <div class="thread-name" onclick="toggleStack(this)">Non-RSM Thread: {violation['non_core_thread']['thread_name']}</div>
                <div class="stack-trace">
        '''
        
        for frame in violation['non_core_thread']['stack']:
            method_name = escape_html(frame['method'])
            html += f'''
                    <div class="location">at {frame['class']}.{method_name}({frame['file']}:line {frame['line']})</div>
            '''
            
        html += '''
                </div>
            </div>
        </div>
        '''
    
    html += '''
    </body>
    </html>
    '''
    
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(html)

if __name__ == '__main__':
    log_file = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'logs', 'thread-monitor.log')
    output_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'output')
    os.makedirs(output_dir, exist_ok=True)
    
    violations, excluded_items, ignored_static_inits = parse_log_file(log_file)
    output_file = os.path.join(output_dir, 'violations_report.html')
    generate_html(violations, excluded_items, ignored_static_inits, output_file)
    
    print(f"HTML report generated: {output_file}")
    print("Open this file in your browser to view the report") 